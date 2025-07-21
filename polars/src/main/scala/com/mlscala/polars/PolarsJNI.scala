package com.mlscala.polars

import java.io.{File, InputStream}
import java.nio.file.{Files, Paths, StandardCopyOption}
import scala.util.{Try, Using}

class PolarsJNI {
  // Native method declarations
  @native def createSampleDataFrame(): String
  @native def readCsv(path: String): String
  @native def filterDataFrame(csvPath: String, column: String, minValue: Double): String
  @native def groupByAndSum(csvPath: String, groupCol: String, sumCol: String): String
}

object PolarsJNI {
  // Load native library
  private val libraryLoaded: Boolean = loadNativeLibrary()
  
  // Singleton instance
  private val instance = new PolarsJNI()

  private def loadNativeLibrary(): Boolean = {
    try {
      val libraryName = System.mapLibraryName("rust_polars")
      val resourcePath = s"/native/$libraryName"
      
      // Try to load from resources first
      Option(getClass.getResourceAsStream(resourcePath)) match {
        case Some(stream) =>
          Using(stream) { is =>
            val tempFile = Files.createTempFile("rust_polars", ".so")
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING)
            System.load(tempFile.toString)
            tempFile.toFile.deleteOnExit()
          }.isSuccess
        case None =>
          // Try to load from library path
          System.loadLibrary("rust_polars")
          true
      }
    } catch {
      case e: Exception =>
        System.err.println(s"Failed to load native library: ${e.getMessage}")
        false
    }
  }

  private def ensureLibraryLoaded(): Unit = {
    if (!libraryLoaded) {
      throw new RuntimeException("Native library not loaded. Please ensure rust-polars library is available.")
    }
  }

  // Scala wrapper methods with error handling
  inline def createSampleDataFrameSafe(): Either[String, String] = {
    Try {
      ensureLibraryLoaded()
      instance.createSampleDataFrame()
    }.toEither.left.map(_.getMessage)
  }

  inline def readCsvSafe(path: String): Either[String, String] = {
    Try {
      ensureLibraryLoaded()
      if (!Files.exists(Paths.get(path))) {
        throw new IllegalArgumentException(s"File not found: $path")
      }
      instance.readCsv(path)
    }.toEither.left.map(_.getMessage)
  }

  inline def filterDataFrameSafe(csvPath: String, column: String, minValue: Double): Either[String, String] = {
    Try {
      ensureLibraryLoaded()
      if (!Files.exists(Paths.get(csvPath))) {
        throw new IllegalArgumentException(s"File not found: $csvPath")
      }
      instance.filterDataFrame(csvPath, column, minValue)
    }.toEither.left.map(_.getMessage)
  }

  inline def groupByAndSumSafe(csvPath: String, groupCol: String, sumCol: String): Either[String, String] = {
    Try {
      ensureLibraryLoaded()
      if (!Files.exists(Paths.get(csvPath))) {
        throw new IllegalArgumentException(s"File not found: $csvPath")
      }
      instance.groupByAndSum(csvPath, groupCol, sumCol)
    }.toEither.left.map(_.getMessage)
  }
}