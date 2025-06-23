ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

lazy val root = (project in file("."))
  .settings(
    name := "chatops4s-discord",
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % "2.36.5",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.34",
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.34",
      "com.softwaremill.sttp.tapir" %% "tapir-netty-server-sync" % "1.11.34",
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.34",
      "com.softwaremill.sttp.client4" %% "core" % "4.0.8",
      "org.bouncycastle" % "bcpkix-jdk15on" % "1.70",
      "org.scala-lang.modules" %% "scala-parallel-collections" % "1.2.0",
      "io.circe" %% "circe-core" % "0.14.14",
      "io.circe" %% "circe-generic" % "0.14.14",
      "io.circe" %% "circe-parser" % "0.14.14"
    ))
  )
