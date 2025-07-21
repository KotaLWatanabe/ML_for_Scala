package com.mlscala.polars

import cats.effect.IO
import io.circe.parser.decode
import io.circe.{Decoder, Json}

case class DataFrameResult(data: List[Json], rowCount: Int)

object DataFrameOps {
  
  inline def createSampleDataFrame(): IO[Either[String, DataFrameResult]] = {
    IO {
      PolarsJNI.createSampleDataFrameSafe().flatMap(parseDataFrameJson)
    }
  }

  inline def readCsvFile(path: String): IO[Either[String, DataFrameResult]] = {
    IO {
      PolarsJNI.readCsvSafe(path).flatMap(parseDataFrameJson)
    }
  }

  inline def filterDataFrame(csvPath: String, column: String, minValue: Double): IO[Either[String, DataFrameResult]] = {
    IO {
      PolarsJNI.filterDataFrameSafe(csvPath, column, minValue).flatMap(parseDataFrameJson)
    }
  }

  inline def groupByAndSum(csvPath: String, groupCol: String, sumCol: String): IO[Either[String, DataFrameResult]] = {
    IO {
      PolarsJNI.groupByAndSumSafe(csvPath, groupCol, sumCol).flatMap(parseDataFrameJson)
    }
  }

  private inline def parseDataFrameJson(jsonStr: String): Either[String, DataFrameResult] = {
    decode[List[Json]](jsonStr) match {
      case Right(data) => Right(DataFrameResult(data, data.length))
      case Left(error) => Left(s"JSON parsing error: ${error.getMessage}")
    }
  }

  // Helper methods for data extraction
  def extractColumn[T](result: DataFrameResult, columnName: String)(implicit decoder: Decoder[T]): Either[String, List[T]] = {
    val values = result.data.flatMap { row =>
      row.hcursor.get[T](columnName).toOption
    }
    
    if (values.length == result.rowCount) {
      Right(values)
    } else {
      Left(s"Could not extract all values for column: $columnName")
    }
  }

  def printDataFrame(result: DataFrameResult): IO[Unit] = {
    IO {
      if (result.data.nonEmpty) {
        // Print header
        val columns = result.data.head.asObject.map(_.keys.toList).getOrElse(List.empty)
        println(columns.mkString("\t"))
        println("-" * (columns.length * 15))
        
        // Print rows
        result.data.foreach { row =>
          val values = columns.map { col =>
            row.hcursor.get[String](col).getOrElse("null")
          }
          println(values.mkString("\t"))
        }
        println(s"\nTotal rows: ${result.rowCount}")
      } else {
        println("Empty DataFrame")
      }
    }
  }
}