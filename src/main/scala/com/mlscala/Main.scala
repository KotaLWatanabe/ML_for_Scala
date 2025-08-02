
package com.mlscala

import cats.effect.{IO, IOApp, ExitCode}
import com.mlscala.plotting.*
import com.mlscala.polars.DataFrameOps
import java.nio.file.{Files, Paths}
import java.nio.charset.StandardCharsets

object Main extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    val program = for
      _ <- IO.println("ML for Scala with Rust Polars Integration")
      _ <- IO.println("=" * 50)
      
      // Create sample CSV file for testing
      _ <- createSampleCsv()
      
      // Test 1: Create and display sample DataFrame
      _ <- IO.println("\n1. Creating sample DataFrame...")
      sampleResult <- DataFrameOps.createSampleDataFrame()
      _ <- sampleResult match {
        case Right(df) => DataFrameOps.printDataFrame(df)
        case Left(error) => IO.println(s"Error: $error")
      }
      
      // Test 2: Read CSV file
      _ <- IO.println("\n2. Reading CSV file...")
      csvResult <- DataFrameOps.readCsvFile("sample_data.csv")
      _ <- csvResult match {
        case Right(df) => DataFrameOps.printDataFrame(df)
        case Left(error) => IO.println(s"Error: $error")
      }
      
      // Test 3: Filter DataFrame
      _ <- IO.println("\n3. Filtering data (salary >= 60000)...")
      filterResult <- DataFrameOps.filterDataFrame("sample_data.csv", "salary", 60000.0)
      _ <- filterResult match {
        case Right(df) => DataFrameOps.printDataFrame(df)
        case Left(error) => IO.println(s"Error: $error")
      }
      
      // Test 4: Group by and sum
      _ <- IO.println("\n4. Group by department and sum salaries...")
      groupResult <- DataFrameOps.groupByAndSum("sample_data.csv", "department", "salary")
      _ <- groupResult match {
        case Right(df) => DataFrameOps.printDataFrame(df)
        case Left(error) => IO.println(s"Error: $error")
      }
      
      _ <- IO.println("\n" + "=" * 50)
      _ <- IO.println("Polars integration completed successfully!")
      
      // Also run plotting demo
      _ <- IO.println("\nRunning plotting demo...")
      _ <- runPlottingDemo()
      
      // Run streaming demo
      _ <- IO.println("\nRunning streaming processing demo...")
      _ <- StreamingDemo.run(List.empty).void
      
      // Run Rust streaming demo
      _ <- IO.println("\nRunning Rust streaming demo...")
      _ <- RustStreamingDemo.run(List.empty).void
      
    yield ExitCode.Success
    
    program.handleErrorWith { error =>
      IO.println(s"Application error: ${error.getMessage}") *> 
      IO.pure(ExitCode.Error)
    }
  
  private inline def createSampleCsv(): IO[Unit] =
    IO {
      val csvContent = 
        """name,age,salary,department
          |Alice,25,50000,Engineering
          |Bob,30,60000,Sales
          |Charlie,35,70000,Engineering
          |Diana,28,55000,Marketing
          |Eve,32,65000,Sales
          |Frank,29,58000,Engineering
          |Grace,31,72000,Marketing
          |Henry,27,48000,Sales""".stripMargin
      
      Files.write(
        Paths.get("sample_data.csv"),
        csvContent.getBytes(StandardCharsets.UTF_8)
      )
      println("Created sample CSV file: sample_data.csv")
    }

  private def runPlottingDemo(): IO[Unit] =
    for
      _ <- IO.println("Generating sample plots...")
      
      // Generate sample data
      sinPoints <- PlotDataGenerator.generateSinWave(100, amplitude = 2.0)
      randomPoints <- PlotDataGenerator.generateRandomPoints(50)
      linearPoints <- PlotDataGenerator.generateLinearData(slope = 2.0, intercept = 1.0, noise = 0.5)
      
      // Create Plotly service
      plotService = PlotlyService()
      
      // Create different types of plots
      _ <- IO.println("Creating sin wave plot...")
      sinPlot <- plotService.createLinePlot(sinPoints, "Sin Wave")
      _ <- PlotlyRenderer.saveToFile(sinPlot, "sin_wave.html")
      
      _ <- IO.println("Creating scatter plot...")
      scatterPlot <- plotService.createScatterPlot(randomPoints, "Random Data Points")
      _ <- PlotlyRenderer.saveToFile(scatterPlot, "scatter_plot.html")
      
      _ <- IO.println("Creating multi-series plot...")
      multiSeries <- plotService.createMultiSeriesPlot(
        List(
          ("Sin Wave", sinPoints),
          ("Linear Trend", linearPoints)
        ),
        "Multi-Series Comparison"
      )
      _ <- PlotlyRenderer.saveToFile(multiSeries, "multi_series.html")
      
      _ <- IO.println("Creating bar chart...")
      barChart <- plotService.createBarChart(
        List("A", "B", "C", "D", "E"),
        List(10.0, 25.0, 15.0, 30.0, 20.0),
        "Sample Bar Chart"
      )
      _ <- PlotlyRenderer.saveToFile(barChart, "bar_chart.html")
      
      _ <- IO.println("All plots generated successfully!")
      _ <- IO.println("Files created: sin_wave.html, scatter_plot.html, multi_series.html, bar_chart.html")
    yield ()
