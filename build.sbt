ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.6"
ThisBuild / libraryDependencies ++= Seq(
  // Core dependencies
  "org.typelevel" %% "cats-effect" % "3.6.1",

  // HTTP client dependencies
  "com.softwaremill.sttp.client4" %% "core" % "4.0.9",
  "com.softwaremill.sttp.client4" %% "cats" % "4.0.9",
  "com.softwaremill.sttp.client4" %% "circe" % "4.0.9",
  "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.35",
  "com.softwaremill.sttp.tapir" %% "tapir-cats" % "1.11.35",
  "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.11.35",
  "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.35",
  "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.35",
  "org.http4s" %% "http4s-ember-server" % "0.23.30",
  "org.http4s" %% "http4s-ember-client" % "0.23.30",
  // JSON handling
  "io.circe" %% "circe-core" % "0.14.14",
  "io.circe" %% "circe-generic" % "0.14.14",
  "io.circe" %% "circe-parser" % "0.14.14",

  // Configuration
  "com.github.pureconfig" %% "pureconfig-core" % "0.17.9",
  "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.9",

  // Logging
  "ch.qos.logback" % "logback-classic" % "1.5.18",

  // Testing
  "org.scalatest" %% "scalatest" % "3.2.19" % Test
)

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked"
)

lazy val root = (project in file("."))
  .settings(
    name := "chatops4s",
    publish / skip := true
  )
  .aggregate(core, slack, examples)

lazy val core = (project in file("chatops4s-core"))
  .settings(
    name := "chatops4s-core"
  )

lazy val slack = (project in file("chatops4s-slack"))
  .settings(
    name := "chatops4s-slack"
  )
  .dependsOn(core)

lazy val examples = (project in file("chatops4s-examples"))
  .settings(
    name := "chatops4s-examples"
  )
  .dependsOn(core, slack)