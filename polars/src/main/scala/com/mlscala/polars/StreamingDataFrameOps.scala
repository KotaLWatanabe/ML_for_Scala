package com.mlscala.polars

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.{Stream, Pipe}
import fs2.io.file.{Files, Path}
import fs2.text
import io.circe.{Json, parser}
import io.circe.syntax.*
import java.nio.file.{Paths}
import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

// Streaming DataFrame operations for large CSV processing
object StreamingDataFrameOps {

  // Configuration for streaming operations
  case class StreamingConfig(
    batchSize: Int = 1000,
    maxConcurrency: Int = 4,
    bufferSize: Int = 64 * 1024 // 64KB buffer
  )

  // Batch result for streaming operations
  case class StreamingBatch(
    data: List[Json],
    batchIndex: Int,
    totalProcessed: Long
  )

  // Stream CSV file in batches for processing
  inline def streamCsvBatches(
    filePath: String, 
    config: StreamingConfig = StreamingConfig()
  ): Stream[IO, List[String]] = {
    Files[IO]
      .readAll(Path(filePath))
      .through(text.utf8Decode)
      .through(text.lines)
      .drop(1) // Skip header
      .filter(_.trim.nonEmpty)
      .chunkN(config.batchSize)
      .map(_.toList)
  }

  // Process large CSV files with streaming and batching
  inline def processLargeCsv(
    filePath: String,
    processor: List[String] => IO[Either[String, List[Json]]],
    config: StreamingConfig = StreamingConfig()
  ): Stream[IO, Either[String, StreamingBatch]] = {
    streamCsvBatches(filePath, config)
      .zipWithIndex
      .parEvalMap(config.maxConcurrency) { case (batch, index) =>
        processor(batch).map { result =>
          result.map { processedData =>
            StreamingBatch(
              data = processedData,
              batchIndex = index.toInt,
              totalProcessed = (index + 1) * config.batchSize
            )
          }
        }
      }
  }

  // Stream large CSV through Polars processing
  inline def streamPolarsProcessing(
    filePath: String,
    operation: String => IO[Either[String, String]],
    config: StreamingConfig = StreamingConfig()
  ): Stream[IO, Either[String, StreamingBatch]] = {
    processLargeCsv(
      filePath,
      batch => processBatchWithPolars(batch, operation),
      config
    )
  }

  // Process a batch of CSV lines using Polars operations
  private inline def processBatchWithPolars(
    csvLines: List[String],
    operation: String => IO[Either[String, String]]
  ): IO[Either[String, List[Json]]] = {
    for {
      // Create temporary CSV content for this batch
      header <- IO.pure("name,age,salary,department") // Should be extracted from original file
      csvContent = (header :: csvLines).mkString("\n")
      
      // Write temporary file
      tempFile <- IO.pure(s"temp_batch_${System.currentTimeMillis()}.csv")
      _ <- writeTempCsv(tempFile, csvContent)
      
      // Process with Polars
      result <- operation(tempFile)
      
      // Parse result
      parsedResult <- result match {
        case Right(jsonStr) => parseJsonToList(jsonStr).pure[IO]
        case Left(error) => IO.pure(Left(error))
      }
      
      // Cleanup temp file  
      _ <- Files[IO].delete(Path(tempFile)).handleError(_ => ())
      
    } yield parsedResult
  }

  // Filter large CSV files with streaming
  inline def streamFilterCsv(
    filePath: String,
    column: String,
    minValue: Double,
    config: StreamingConfig = StreamingConfig()
  ): Stream[IO, Either[String, StreamingBatch]] = {
    streamPolarsProcessing(
      filePath,
      tempFile => DataFrameOps.filterDataFrame(tempFile, column, minValue).map(_.map(_.data.asJson.spaces2)),
      config
    )
  }

  // Aggregate results from streaming operations
  inline def aggregateStreamResults[T](
    stream: Stream[IO, Either[String, T]],
    aggregator: (List[T], T) => List[T] = (acc: List[T], item: T) => acc :+ item
  ): IO[Either[String, List[T]]] = {
    stream
      .compile
      .fold(Right(List.empty[T]): Either[String, List[T]]) { (acc, item) =>
        (acc, item) match {
          case (Right(accList), Right(value)) => Right(aggregator(accList, value))
          case (Left(error), _) => Left(error)
          case (_, Left(error)) => Left(error)
        }
      }
  }

  // Count total rows in large CSV with streaming
  inline def countRowsStreaming(filePath: String): IO[Long] = {
    Files[IO]
      .readAll(Path(filePath))
      .through(text.utf8Decode)
      .through(text.lines)
      .filter(_.trim.nonEmpty)
      .compile
      .count
      .map(_ - 1) // Subtract header row
  }

  // Progress monitoring for streaming operations
  case class ProcessingProgress(
    processedBatches: Int,
    totalRows: Long,
    currentBatch: Int,
    errors: List[String]
  )

  // Stream with progress monitoring
  inline def streamWithProgress(
    filePath: String,
    operation: String => IO[Either[String, String]],
    config: StreamingConfig = StreamingConfig(),
    progressCallback: ProcessingProgress => IO[Unit] = _ => IO.unit
  ): Stream[IO, Either[String, StreamingBatch]] = {
    streamPolarsProcessing(filePath, operation, config)
      .evalTap { result =>
        result match {
          case Right(batch) =>
            progressCallback(ProcessingProgress(
              processedBatches = batch.batchIndex + 1,
              totalRows = batch.totalProcessed,
              currentBatch = batch.batchIndex,
              errors = List.empty
            ))
          case Left(error) =>
            progressCallback(ProcessingProgress(
              processedBatches = 0,
              totalRows = 0,
              currentBatch = 0,
              errors = List(error)
            ))
        }
      }
  }

  // Save streaming results to output file
  inline def saveStreamingResults(
    stream: Stream[IO, Either[String, StreamingBatch]],
    outputPath: String
  ): IO[Either[String, Long]] = {
    val outputFile = Path(outputPath)
    
    stream
      .evalMap {
        case Right(batch) =>
          val jsonLines = batch.data.map(_.spaces2).mkString("\n") + "\n"
          IO {
            java.nio.file.Files.write(
              Paths.get(outputPath),
              jsonLines.getBytes(StandardCharsets.UTF_8),
              java.nio.file.StandardOpenOption.CREATE,
              java.nio.file.StandardOpenOption.APPEND
            )
          }.as(Right(batch.data.size.toLong))
        case Left(error) =>
          IO.pure(Left(error))
      }
      .compile
      .fold(Right(0L): Either[String, Long]) { (acc, result) =>
        (acc, result) match {
          case (Right(count), Right(batchCount)) => Right(count + batchCount.toLong)
          case (Left(error), _) => Left(error)
          case (_, Left(error)) => Left(error)
        }
      }
  }

  // Utility functions
  private inline def writeTempCsv(fileName: String, content: String): IO[Unit] = {
    IO {
      java.nio.file.Files.write(
        Paths.get(fileName),
        content.getBytes(StandardCharsets.UTF_8)
      )
    }.void
  }

  private inline def parseJsonToList(jsonStr: String): Either[String, List[Json]] = {
    parser.decode[List[Json]](jsonStr)
      .left.map(error => s"JSON parsing error: ${error.getMessage}")
  }

  // Memory-efficient CSV processing with backpressure
  inline def processLargeCsvWithBackpressure(
    filePath: String,
    processor: List[String] => IO[Either[String, List[Json]]],
    config: StreamingConfig = StreamingConfig()
  ): Stream[IO, Either[String, StreamingBatch]] = {
    streamCsvBatches(filePath, config)
      .zipWithIndex
      .buffer(config.maxConcurrency * 2) // Buffer for backpressure control
      .mapAsync(config.maxConcurrency) { case (batch, index) =>
        processor(batch).map { result =>
          result.map { processedData =>
            StreamingBatch(
              data = processedData,
              batchIndex = index.toInt,
              totalProcessed = (index + 1) * config.batchSize
            )
          }
        }
      }
  }
}