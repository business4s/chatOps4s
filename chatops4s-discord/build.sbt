ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    name := "chatops4s-discord",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"             % "1.11.34",
      "com.softwaremill.sttp.tapir" %% "tapir-cats"             % "1.11.34",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"    % "1.11.34",
      "org.http4s" %% "http4s-blaze-server"    % "0.23.17",
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.34",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server"     % "1.11.34",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"       % "1.11.34",
      "com.softwaremill.sttp.client4" %% "core"                 % "4.0.9",
      "com.softwaremill.sttp.client4" %% "circe"                % "4.0.9",
      "com.softwaremill.sttp.client4" %% "cats"                 % "4.0.9",
      "io.circe" %% "circe-parser"                              % "0.14.14",
      "org.typelevel" %% "cats-effect"                          % "3.6.1"
    )
  )