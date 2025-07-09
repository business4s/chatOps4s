ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "org.business4s"

lazy val root = (project in file("."))
  .settings(
    name := "chatops4s-discord",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"             % "1.11.35",
      "com.softwaremill.sttp.tapir" %% "tapir-cats"             % "1.11.35",
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.70",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"       % "1.11.35",
      "com.softwaremill.sttp.client4" %% "core"                 % "4.0.9",
      "com.softwaremill.sttp.client4" %% "circe"                % "4.0.9",
      "com.softwaremill.sttp.client4" %% "cats"                 % "4.0.9",
      "io.circe" %% "circe-parser"                              % "0.14.14",
      "org.typelevel" %% "cats-effect"                          % "3.6.2",
      "com.typesafe.scala-logging" %% "scala-logging"           % "3.9.5"
    )
  )