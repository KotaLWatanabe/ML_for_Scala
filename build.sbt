import Libraries.*

ThisBuild / organization := "com.mlscala"
ThisBuild / scalaVersion := "3.7.1"
ThisBuild / semanticdbEnabled := true

// Exclude nativeImageVersion from unused key warnings
Global / excludeLintKeys += nativeImageVersion

lazy val polars = (project in file("polars"))
  .settings(
    name := "mlscala-polars",
    version := "0.0.1",
    
    // Output directories
    Compile / target := baseDirectory.value / "out" / "scala" / "compile",
    Test / target := baseDirectory.value / "out" / "scala" / "test",
    target := baseDirectory.value / "out" / "scala",
    
    // Dependencies specific to Polars functionality
    libraryDependencies ++= Seq(
      Libraries.cats,
      Libraries.catsEffect,
      Libraries.logback
    ) ++ Libraries.circe,
    
    // JVM options for running with JNI
    Compile / run / javaOptions ++= Seq(
      "-Djava.library.path=../out/resources/native",
      "-Dcats.effect.warnOnNonMainThreadDetected=false",
      "--add-opens=java.base/sun.misc=ALL-UNNAMED",
      "--enable-native-access=ALL-UNNAMED"
    ),
    
    // Fork JVM to properly handle JNI
    Compile / run / fork := true
  )

lazy val plotting = (project in file("plotting"))
  .settings(
    name := "mlscala-plotting",
    version := "0.0.1",
    
    // Output directories
    Compile / target := baseDirectory.value / "out" / "scala" / "compile",
    Test / target := baseDirectory.value / "out" / "scala" / "test",
    target := baseDirectory.value / "out" / "scala",
    
    // Dependencies specific to Plotting functionality
    libraryDependencies ++= Seq(
      Libraries.cats,
      Libraries.catsEffect,
      Libraries.logback
    ) ++ Libraries.circe,
    
    // Fork JVM to properly handle JNI
    Compile / run / fork := true
  )

lazy val root = (project in file("."))
  .enablePlugins(NativeImagePlugin)
  .dependsOn(polars, plotting)
  .aggregate(polars, plotting)
  .settings(
    name := "mlscala",
    version := "0.0.1",
    
    // Output directories
    Compile / target := baseDirectory.value / "out" / "scala" / "compile",
    Test / target := baseDirectory.value / "out" / "scala" / "test",
    target := baseDirectory.value / "out" / "scala",
    
    // Native image configuration
    nativeImageOutput := baseDirectory.value / "out" / "native-image" / "mlscala",
    nativeImageOptions ++= List(
      "-H:+AllowIncompleteClasspath",
      "--no-fallback",
      "-H:+ReportExceptionStackTraces",
      "-H:+JNI",
      "--enable-url-protocols=http,https"
    ),
    nativeImageVersion  := "22.1.0",
    libraryDependencies ++= Libraries.common,
    Compile / mainClass := Some("com.mlscala.Main"),
    
    // JVM options for running with JNI
    Compile / run / javaOptions ++= Seq(
      "-Djava.library.path=./out/resources/native",
      "-Dcats.effect.warnOnNonMainThreadDetected=false",
      "--add-opens=java.base/sun.misc=ALL-UNNAMED",
      "--enable-native-access=ALL-UNNAMED"
    ),
    // Fork JVM to properly handle JNI
    Compile / run / fork := true
  )
