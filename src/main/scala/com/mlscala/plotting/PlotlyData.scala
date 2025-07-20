package com.mlscala.plotting

import io.circe.{Encoder, Json}
import io.circe.generic.semiauto.*
import io.circe.syntax.*

case class Point(x: Double, y: Double)

sealed trait ChartType
object ChartType:
  case object Scatter extends ChartType
  case object Line extends ChartType
  case object Bar extends ChartType
  case object Histogram extends ChartType

  given Encoder[ChartType] = Encoder.instance {
    case Scatter => Json.fromString("scatter")
    case Line => Json.fromString("scatter")
    case Bar => Json.fromString("bar")
    case Histogram => Json.fromString("histogram")
  }

case class TraceConfig(
  name: Option[String] = None,
  color: Option[String] = None,
  width: Option[Int] = None,
  mode: Option[String] = None
)

object TraceConfig:
  given Encoder[TraceConfig] = deriveEncoder

case class Trace(
  x: List[Double],
  y: List[Double],
  `type`: ChartType,
  name: Option[String] = None,
  mode: Option[String] = None,
//  line: Option[Json] = None,
//  marker: Option[Json] = None
)

object Trace:
  given Encoder[Trace] = deriveEncoder

  def scatter(points: List[Point], config: TraceConfig = TraceConfig()): Trace =
    Trace(
      x = points.map(_.x),
      y = points.map(_.y),
      `type` = ChartType.Scatter,
      name = config.name,
      mode = config.mode.orElse(Some("markers"))
    )

  def line(points: List[Point], config: TraceConfig = TraceConfig()): Trace =
    Trace(
      x = points.map(_.x),
      y = points.map(_.y),
      `type` = ChartType.Line,
      name = config.name,
      mode = config.mode.orElse(Some("lines"))
    )

  def bar(x: List[Double], y: List[Double], config: TraceConfig = TraceConfig()): Trace =
    Trace(
      x = x,
      y = y,
      `type` = ChartType.Bar,
      name = config.name
    )

case class Layout(
  title: Option[String] = None,
//  xaxis: Option[Json] = None,
//  yaxis: Option[Json] = None,
//  width: Option[Int] = None,
//  height: Option[Int] = None
)

object Layout:
  given Encoder[Layout] = deriveEncoder

  def withTitle(title: String): Layout = Layout(title = Some(title))

case class PlotData(
  traces: List[Trace],
  layout: Layout = Layout()
)

object PlotData:
  given Encoder[PlotData] = Encoder.instance { plotData =>
    Json.obj(
      "data" -> plotData.traces.asJson,
      "layout" -> plotData.layout.asJson
    )
  }