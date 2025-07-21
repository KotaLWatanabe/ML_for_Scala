package com.mlscala.plotting

import cats.effect.IO
import cats.syntax.traverse.*
import scala.util.Random

trait PlotlyService:
  def createScatterPlot(points: List[Point], title: String): IO[PlotData]
  def createLinePlot(points: List[Point], title: String): IO[PlotData]
  def createBarChart(labels: List[String], values: List[Double], title: String): IO[PlotData]
  def createMultiSeriesPlot(series: List[(String, List[Point])], title: String): IO[PlotData]

object PlotlyService:
  def apply(): PlotlyService = new PlotlyServiceImpl

  private class PlotlyServiceImpl extends PlotlyService:

    def createScatterPlot(points: List[Point], title: String): IO[PlotData] =
      IO {
        val trace = Trace.scatter(points, TraceConfig(name = Some("Data Points")))
        PlotData(
          traces = List(trace),
          layout = Layout.withTitle(title)
        )
      }

    def createLinePlot(points: List[Point], title: String): IO[PlotData] =
      IO {
        val trace = Trace.line(points, TraceConfig(name = Some("Line")))
        PlotData(
          traces = List(trace),
          layout = Layout.withTitle(title)
        )
      }

    def createBarChart(labels: List[String], values: List[Double], title: String): IO[PlotData] =
      IO {
        val x = labels.zipWithIndex.map(_._2.toDouble)
        val trace = Trace.bar(x, values, TraceConfig(name = Some("Values")))
        PlotData(
          traces = List(trace),
          layout = Layout.withTitle(title)
        )
      }

    def createMultiSeriesPlot(series: List[(String, List[Point])], title: String): IO[PlotData] =
      IO {
        val traces = series.map { case (name, points) =>
          Trace.line(points, TraceConfig(name = Some(name)))
        }
        PlotData(
          traces = traces,
          layout = Layout.withTitle(title)
        )
      }

object PlotDataGenerator:
  
  inline def generateRandomPoints(count: Int, xRange: (Double, Double) = (0.0, 10.0)): IO[List[Point]] =
    IO {
      val random = new Random()
      (1 to count).map { _ =>
        val x = xRange._1 + random.nextDouble() * (xRange._2 - xRange._1)
        val y = math.sin(x) + random.nextGaussian() * 0.1
        Point(x, y)
      }.toList.sortBy(_.x)
    }
  
  inline def generateSinWave(points: Int = 100, amplitude: Double = 1.0, frequency: Double = 1.0): IO[List[Point]] =
    IO {
      (0 until points).map { i =>
        val x = i * 0.1
        val y = amplitude * math.sin(frequency * x)
        Point(x, y)
      }.toList
    }
  
  inline def generateLinearData(slope: Double = 1.0, intercept: Double = 0.0, noise: Double = 0.0): IO[List[Point]] =
    IO {
      val random = new Random()
      (0 to 50).map { i =>
        val x = i.toDouble
        val y = slope * x + intercept + (if noise > 0 then random.nextGaussian() * noise else 0)
        Point(x, y)
      }.toList
    }