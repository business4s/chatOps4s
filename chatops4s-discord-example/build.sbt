ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.6"
ThisBuild / organization := "org.business4s"

lazy val chatops4sDiscord = project.in(file("chatops4s-discord"))

lazy val root = (project in file("."))
  .dependsOn(chatops4sDiscord)
  .settings(
    name := "chatops4s-discord",
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"             % "1.11.34",
      "com.softwaremill.sttp.tapir" %% "tapir-cats"             % "1.11.34",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"    % "1.11.34",
      "org.http4s" %% "http4s-blaze-server"                     % "0.23.17",
      "org.typelevel" %% "cats-effect"                          % "3.6.1"
    )
  )