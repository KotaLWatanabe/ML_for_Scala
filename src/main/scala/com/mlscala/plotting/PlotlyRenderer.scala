package com.mlscala.plotting

import cats.effect.IO
import io.circe.syntax.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths, StandardOpenOption}

object PlotlyRenderer:

  private val htmlTemplate =
    // language=HTML
    """<!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <title>Plotly Graph</title>
        </head>
        <body>
            <div id="plot"></div>
<script type="module">
    import "https://cdn.plot.ly/plotly-3.1.0-rc.0.min.js"
                const plotData = {{PLOT_DATA}};
                Plotly.newPlot('plot', plotData);
            </script>
        </body>
        </html>"""

  def renderToHtml(plotData: PlotData): IO[String] =
    IO {
      val jsonData = plotData.asJson.spaces2
      htmlTemplate.replace("{{PLOT_DATA}}", jsonData)
    }

  def saveToFile(plotData: PlotData, filePath: String): IO[Unit] =
    for
      html <- renderToHtml(plotData)
      path = Paths.get(filePath)
      _ <- IO.blocking {
        Files.write(
          path,
          html.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING
        )
      }
      _ <- IO.println(s"Graph saved to: $filePath")
    yield ()

  def openInBrowser(plotData: PlotData, fileName: String = "plot.html"): IO[Unit] =
    for
      _ <- saveToFile(plotData, fileName)
      _ <- IO.blocking {
        val os = System.getProperty("os.name").toLowerCase
        val command = if os.contains("win") then
          Array("cmd", "/c", "start", fileName)
        else if os.contains("mac") then
          Array("open", fileName)
        else
          Array("xdg-open", fileName)

        Runtime.getRuntime.exec(command)
      }
      _ <- IO.println(s"Opening $fileName in browser...")
    yield ()