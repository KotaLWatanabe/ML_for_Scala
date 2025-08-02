package com.mlscala

import cats.effect.{IO, IOApp, ExitCode}
import cats.syntax.all.*
import com.mlscala.polars.{RustStreamingOps, DataFrameOps}
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets
import scala.util.Random

object RustStreamingDemo extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    val program = for {
      _ <- IO.println("🚀 Rust Streaming CSV Processing Demo")
      _ <- IO.println("=" * 50)
      
      // Create test files of different sizes
      _ <- createTestFiles()
      
      // Demo 1: Pure Rust streaming processing
      _ <- IO.println("\n1. 🦀 Pure Rust streaming processing...")
      rustResult <- RustStreamingOps.processLargeCsvInRust(
        "large_test.csv",
        RustStreamingOps.Operations.filter("salary", 60000.0)
      )
      _ <- rustResult match {
        case Right(df) => 
          IO.println(s"✅ Rust processed ${df.rowCount} rows") *>
          IO.println(s"Sample data: ${df.data.take(3).map(_.spaces2).mkString(", ")}")
        case Left(error) => 
          IO.println(s"❌ Rust processing failed: $error")
      }

      // Demo 2: Hybrid fs2 + Rust processing
      _ <- IO.println("\n2. 🔄 Hybrid fs2 + Rust processing...")
      hybridResult <- RustStreamingOps.hybridStreamProcessing(
        "medium_test.csv",
        RustStreamingOps.Operations.groupBy("department", "salary")
      )
      
      _ <- hybridResult match {
        case Right(jsonList) => 
          IO.println(s"✅ Hybrid processing completed: ${jsonList.size} results") *>
          jsonList.take(3).traverse_(json => IO.println(s"  📊 ${json.spaces2}"))
        case Left(error) => 
          IO.println(s"  ❌ Error: $error")
      }

      // Demo 3: Stream with progress monitoring
      _ <- IO.println("\n3. 📈 Streaming with progress monitoring...")
      progressResult <- RustStreamingOps.streamWithRustProgress(
        "large_test.csv",
        RustStreamingOps.Operations.filter("age", 25.0),
        RustStreamingOps.RustStreamConfig(chunkSizeBytes = 512 * 1024),
        (processed, total) => IO.println(f"Progress: ${processed * 100.0 / total}%2.1f%% ($processed/$total bytes)")
      )
      
      _ <- progressResult match {
        case Right(df) => IO.println(s"✅ Progress processing completed: ${df.rowCount} rows")
        case Left(error) => IO.println(s"❌ Progress processing failed: $error")
      }

      // Demo 4: Batch process multiple files
      _ <- IO.println("\n4. 📚 Batch processing multiple files...")
      batchResults <- RustStreamingOps.batchProcessFiles(
        List("small_test.csv", "medium_test.csv"),
        RustStreamingOps.Operations.passthrough,
        maxConcurrency = 2
      ).compile.toList
      
      _ <- batchResults.traverse_ {
        case Right((fileName, df)) => 
          IO.println(s"✅ $fileName: ${df.rowCount} rows processed")
        case Left(error) => 
          IO.println(s"❌ Batch processing error: $error")
      }

      // Demo 5: Performance benchmarking
      _ <- IO.println("\n5. 🏁 Performance benchmarking...")
      (rustStats, scalaStats) <- RustStreamingOps.benchmarkProcessing(
        "medium_test.csv",
        RustStreamingOps.Operations.filter("salary", 50000.0)
      )
      
      _ <- IO.println(f"🦀 Rust: ${rustStats.processingTimeMs}ms, ${rustStats.throughputRowsPerSec}%,.0f rows/sec")
      _ <- IO.println(f"📝 Scala: ${scalaStats.processingTimeMs}ms, ${scalaStats.throughputRowsPerSec}%,.0f rows/sec")
      _ <- if (rustStats.processingTimeMs < scalaStats.processingTimeMs) {
        val speedup = scalaStats.processingTimeMs.toDouble / rustStats.processingTimeMs.toDouble
        IO.println(f"🚀 Rust is ${speedup}%.1fx faster!")
      } else {
        IO.println("📊 Performance results may vary with larger datasets")
      }

      // Cleanup
      _ <- cleanupTestFiles()
      
    } yield ExitCode.Success
    
    program.handleErrorWith { error =>
      IO.println(s"💥 Demo error: ${error.getMessage}") *> 
      IO.pure(ExitCode.Error)
    }
  }

  private def createTestFiles(): IO[Unit] = {
    val random = new Random()
    val departments = Array("Engineering", "Sales", "Marketing", "HR", "Finance", "Operations")
    val names = Array("Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry", "Ivy", "Jack")
    
    def generateCsv(rows: Int): String = {
      val header = "name,age,salary,department\n"
      val content = header + (1 to rows).map { i =>
        val name = s"${names(random.nextInt(names.length))}${i}"
        val age = 22 + random.nextInt(40)
        val salary = 40000 + random.nextInt(80000)
        val dept = departments(random.nextInt(departments.length))
        s"$name,$age,$salary,$dept"
      }.mkString("\n")
      content
    }
    
    for {
      _ <- IO {
        Files.write(Paths.get("small_test.csv"), generateCsv(1000).getBytes(StandardCharsets.UTF_8))
        println("📄 Created small_test.csv (1K rows)")
      }
      _ <- IO {
        Files.write(Paths.get("medium_test.csv"), generateCsv(10000).getBytes(StandardCharsets.UTF_8))
        println("📄 Created medium_test.csv (10K rows)")
      }
      _ <- IO {
        Files.write(Paths.get("large_test.csv"), generateCsv(50000).getBytes(StandardCharsets.UTF_8))
        println("📄 Created large_test.csv (50K rows)")
      }
    } yield ()
  }

  private def cleanupTestFiles(): IO[Unit] = {
    IO {
      List("small_test.csv", "medium_test.csv", "large_test.csv").foreach { fileName =>
        Files.deleteIfExists(Paths.get(fileName))
      }
      println("🧹 Cleaned up test files")
    }
  }
}