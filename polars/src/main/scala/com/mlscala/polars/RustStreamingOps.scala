package com.mlscala.polars

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.{Stream, Chunk}
import fs2.io.file.{Files, Path}
import fs2.text
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

// High-performance CSV processing using Rust streaming via JNI
object RustStreamingOps {

  // Configuration for Rust streaming operations
  case class RustStreamConfig(
    chunkSizeBytes: Int = 1024 * 1024, // 1MB chunks for efficient JNI calls
    operation: String = "passthrough"
  )

  // Stream processor resource that manages JNI session
  def createStreamProcessor(operation: String): Resource[IO, Long] = {
    Resource.make(
      PolarsJNI.initStreamProcessorSafe(operation) match {
        case Right(id) => IO.pure(id)
        case Left(error) => IO.raiseError(new RuntimeException(s"Failed to init stream processor: $error"))
      }
    )(processorId =>
      PolarsJNI.closeStreamProcessorSafe(processorId) match {
        case Right(_) => IO.unit
        case Left(error) => IO.println(s"Warning: Failed to close stream processor: $error")
      }
    )
  }

  // Stream CSV file directly through Rust with fs2 integration
  inline def streamCsvThroughRust(
    filePath: String,
    config: RustStreamConfig = RustStreamConfig()
  ): Stream[IO, Either[String, DataFrameResult]] = {
    Stream.resource(createStreamProcessor(config.operation))
      .flatMap { processorId =>
        Files[IO]
          .readAll(Path(filePath))
          .chunkN(config.chunkSizeBytes)
          .evalMap { chunk =>
            processChunkWithRust(processorId, chunk.toArray)
          }
          .evalMap { progressResult =>
            progressResult match {
              case Right(_) => IO.pure(Right(()))  // Continue processing
              case Left(error) => IO.pure(Left(error))
            }
          }
          .drain ++ Stream.eval(getStreamResults(processorId, config.operation))
      }
  }

  // Process CSV file entirely in Rust using lazy evaluation
  inline def processLargeCsvInRust(
    filePath: String,
    operation: String,
    chunkSize: Int = 10000
  ): IO[Either[String, DataFrameResult]] = {
    PolarsJNI.streamProcessCSVSafe(filePath, chunkSize, operation) match {
      case Right(jsonStr) => parseDataFrameJson(jsonStr).pure[IO]
      case Left(error) => IO.pure(Left(error))
    }
  }

  // Hybrid streaming: fs2 reads + Rust processes chunks
  inline def hybridStreamProcessing(
    filePath: String,
    operation: String,
    config: RustStreamConfig = RustStreamConfig()
  ): IO[Either[String, List[Json]]] = {
    createStreamProcessor(operation).use { processorId =>
      for {
        // Process file in chunks
        _ <- Files[IO]
          .readAll(Path(filePath))
          .through(text.utf8Decode)
          .through(text.lines)
          .drop(1) // Skip header
          .filter(_.trim.nonEmpty)
          .chunkN(1000) // Process in smaller logical chunks
          .evalMap { lineChunk =>
            val csvContent = "name,age,salary,department\n" + lineChunk.toList.mkString("\n")
            val chunkBytes = csvContent.getBytes(StandardCharsets.UTF_8)
            processChunkWithRust(processorId, chunkBytes)
          }
          .collect { case Right(_) => () }
          .compile.drain
        
        // Get final results
        result <- getStreamResults(processorId, operation)
      } yield result.map(_.data)
    }
  }

  // Stream with progress monitoring and backpressure
  inline def streamWithRustProgress(
    filePath: String,
    operation: String,
    config: RustStreamConfig = RustStreamConfig(),
    progressCallback: (Long, Long) => IO[Unit] = (_, _) => IO.unit
  ): IO[Either[String, DataFrameResult]] = {
    for {
      totalSize <- Files[IO].size(Path(filePath))
      result <- createStreamProcessor(operation).use { processorId =>
        for {
          _ <- Files[IO]
            .readAll(Path(filePath))
            .chunkN(config.chunkSizeBytes)
            .zipWithIndex
            .evalMap { case (chunk, index) =>
              val processedBytes = (index + 1) * config.chunkSizeBytes
              for {
                _ <- progressCallback(processedBytes, totalSize)
                result <- processChunkWithRust(processorId, chunk.toArray)
              } yield result
            }
            .takeWhile(_.isRight)
            .compile.drain
          result <- getStreamResults(processorId, operation)
        } yield result
      }
    } yield result
  }

  // Batch multiple files processing
  inline def batchProcessFiles(
    filePaths: List[String],
    operation: String,
    maxConcurrency: Int = 4
  ): Stream[IO, Either[String, (String, DataFrameResult)]] = {
    Stream.emits(filePaths)
      .parEvalMap(maxConcurrency) { filePath =>
        processLargeCsvInRust(filePath, operation)
          .map(_.map(result => (filePath, result)))
      }
  }

  // === Private utility methods ===

  private inline def processChunkWithRust(processorId: Long, chunkData: Array[Byte]): IO[Either[String, String]] = {
    PolarsJNI.processCSVChunkSafe(processorId, chunkData) match {
      case Right(result) => IO.pure(Right(result))
      case Left(error) => IO.pure(Left(error))
    }
  }

  private inline def getStreamResults(processorId: Long, operation: String): IO[Either[String, DataFrameResult]] = {
    PolarsJNI.getStreamResultsSafe(processorId, operation) match {
      case Right(jsonStr) => parseDataFrameJson(jsonStr).pure[IO]
      case Left(error) => IO.pure(Left(error))
    }
  }

  private inline def parseDataFrameJson(jsonStr: String): Either[String, DataFrameResult] = {
    parser.decode[List[Json]](jsonStr) match {
      case Right(data) => Right(DataFrameResult(data, data.length))
      case Left(error) => Left(s"JSON parsing error: ${error.getMessage}")
    }
  }

  // === Operation builders ===

  object Operations {
    def filter(column: String, minValue: Double): String = s"filter:$column:$minValue"
    def groupBy(groupCol: String, sumCol: String): String = s"groupby:$groupCol:$sumCol" 
    def passthrough: String = "passthrough"
  }

  // === Performance comparison utilities ===

  case class ProcessingStats(
    processingTimeMs: Long,
    throughputRowsPerSec: Double,
    memoryUsedMB: Long,
    method: String
  )

  inline def benchmarkProcessing(
    filePath: String,
    operation: String
  ): IO[(ProcessingStats, ProcessingStats)] = {
    for {
      // Benchmark Rust processing
      rustStart <- IO.realTime
      rustResult <- processLargeCsvInRust(filePath, operation)
      rustEnd <- IO.realTime
      rustTime = (rustEnd - rustStart).toMillis

      // Benchmark Scala processing (for comparison)
      scalaStart <- IO.realTime  
      scalaResult <- DataFrameOps.readCsvFile(filePath) // Simple read for comparison
      scalaEnd <- IO.realTime
      scalaTime = (scalaEnd - scalaStart).toMillis

      // Calculate stats
      rowCount = rustResult.map(_.rowCount).getOrElse(0)
      rustStats = ProcessingStats(
        rustTime, 
        if (rustTime > 0) rowCount.toDouble / (rustTime.toDouble / 1000.0) else 0.0,
        Runtime.getRuntime.totalMemory() / (1024 * 1024),
        "Rust"
      )
      scalaStats = ProcessingStats(
        scalaTime,
        if (scalaTime > 0) rowCount.toDouble / (scalaTime.toDouble / 1000.0) else 0.0, 
        Runtime.getRuntime.totalMemory() / (1024 * 1024),
        "Scala"
      )
    } yield (rustStats, scalaStats)
  }
}