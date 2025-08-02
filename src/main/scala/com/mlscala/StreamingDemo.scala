package com.mlscala

import cats.effect.{IO, IOApp, ExitCode}
import cats.syntax.all.*
import com.mlscala.polars.{StreamingDataFrameOps, DataFrameOps}
import fs2.Stream
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import scala.util.Random

object StreamingDemo extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val program = for {
      _ <- IO.println("FS2 Streaming CSV Processing Demo")
      _ <- IO.println("=" * 50)
      
      // Create a large sample CSV file
      _ <- createLargeCsv("large_sample.csv", 10000)
      
      // Demo 1: Count rows with streaming
      _ <- IO.println("\n1. Counting rows with streaming...")
      rowCount <- StreamingDataFrameOps.countRowsStreaming("large_sample.csv")
      _ <- IO.println(s"Total rows: $rowCount")
      
      // Demo 2: Process large CSV in batches
      _ <- IO.println("\n2. Processing large CSV in batches...")
      config = StreamingDataFrameOps.StreamingConfig(batchSize = 1000, maxConcurrency = 2)
      
      // Stream and filter with progress monitoring
      progressStream = StreamingDataFrameOps.streamWithProgress(
        "large_sample.csv",
        tempFile => DataFrameOps.filterDataFrame(tempFile, "salary", 60000.0).map(_.map(_.data.toString)),
        config,
        progress => IO.println(s"Progress: Batch ${progress.currentBatch}, Processed ${progress.totalRows} rows")
      )
      
      // Aggregate results
      results <- StreamingDataFrameOps.aggregateStreamResults(
        progressStream.take(5) // Process first 5 batches as demo
      )
      
      _ <- results match {
        case Right(batches) =>
          IO.println(s"Successfully processed ${batches.size} batches") *>
          IO.println(s"Total filtered records: ${batches.map(_.data.size).sum}")
        case Left(error) =>
          IO.println(s"Error in streaming processing: $error")
      }
      
      // Demo 3: Save streaming results
      _ <- IO.println("\n3. Saving streaming results...")
      filterStream = StreamingDataFrameOps.streamFilterCsv(
        "large_sample.csv",
        "salary",
        70000.0,
        config
      ).take(3) // Process first 3 batches
      
      saveResult <- StreamingDataFrameOps.saveStreamingResults(
        filterStream,
        "filtered_output.jsonl"
      )
      
      _ <- saveResult match {
        case Right(count) => IO.println(s"Saved $count records to filtered_output.jsonl")
        case Left(error) => IO.println(s"Error saving results: $error")
      }
      
      // Demo 4: Memory-efficient processing with backpressure
      _ <- IO.println("\n4. Memory-efficient processing with backpressure...")
      backpressureStream = StreamingDataFrameOps.processLargeCsvWithBackpressure(
        "large_sample.csv",
        batch => processBatchExample(batch),
        config
      )
      
      backpressureResults <- backpressureStream
        .take(3)
        .compile
        .toList
      
      _ <- IO.println(s"Backpressure processing completed: ${backpressureResults.size} batches")
      
      // Cleanup
      _ <- IO.blocking {
        Files.deleteIfExists(Paths.get("large_sample.csv"))
        Files.deleteIfExists(Paths.get("filtered_output.jsonl"))
      }
      
    } yield ExitCode.Success
    
    program.handleErrorWith { error =>
      IO.println(s"Streaming demo error: ${error.getMessage}") *> 
      IO.pure(ExitCode.Error)
    }
  }

  // Create a large CSV file for testing
  private def createLargeCsv(fileName: String, rows: Int): IO[Unit] = {
    IO {
      val random = new Random()
      val departments = Array("Engineering", "Sales", "Marketing", "HR", "Finance")
      val names = Array("Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry", "Ivy", "Jack")
      
      val header = "name,age,salary,department\n"
      val content = header + (1 to rows).map { i =>
        val name = s"${names(random.nextInt(names.length))}${i}"
        val age = 22 + random.nextInt(40)
        val salary = 40000 + random.nextInt(80000)
        val dept = departments(random.nextInt(departments.length))
        s"$name,$age,$salary,$dept"
      }.mkString("\n")
      
      Files.write(
        Paths.get(fileName),
        content.getBytes(StandardCharsets.UTF_8)
      )
      println(s"Created large CSV file: $fileName with $rows rows")
    }
  }

  // Example batch processor
  private def processBatchExample(batch: List[String]): IO[Either[String, List[io.circe.Json]]] = {
    IO {
      try {
        // Simulate processing each row
        val processed = batch.map { row =>
          val fields = row.split(",")
          if (fields.length >= 4) {
            io.circe.Json.obj(
              "name" -> io.circe.Json.fromString(fields(0)),
              "age" -> io.circe.Json.fromInt(fields(1).toInt),
              "salary" -> io.circe.Json.fromInt(fields(2).toInt),
              "department" -> io.circe.Json.fromString(fields(3)),
              "processed_at" -> io.circe.Json.fromLong(System.currentTimeMillis())
            )
          } else {
            io.circe.Json.obj("error" -> io.circe.Json.fromString("Invalid row format"))
          }
        }
        Right(processed)
      } catch {
        case e: Exception => Left(s"Batch processing error: ${e.getMessage}")
      }
    }
  }
}