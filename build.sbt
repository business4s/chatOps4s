lazy val `chatops4s` = (project in file("."))
  .settings(commonSettings)
  .settings(
    publish / skip := true
  )
  .aggregate(
    `chatops4s-core`,
    `chatops4s-slack`,
    `chatops4s-examples`
  )

lazy val `chatops4s-core` = (project in file("modules/chatops4s-core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.6.1",
      "org.typelevel" %% "cats-core" % "2.12.0"
    ),
    Test / parallelExecution := false
  )

lazy val `chatops4s-slack` = (project in file("modules/chatops4s-slack"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client4" %% "core" % "4.0.9",
      "com.softwaremill.sttp.client4" %% "cats" % "4.0.9",
      "com.softwaremill.sttp.client4" %% "circe" % "4.0.9",
      "com.softwaremill.sttp.client4" %% "httpclient-backend-cats" % "4.0.9",
      "com.softwaremill.sttp.tapir" %% "tapir-core" % "1.11.36",
      "com.softwaremill.sttp.tapir" %% "tapir-cats" % "1.11.36",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.11.36",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % "1.11.36",
      "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % "1.11.36",
      "org.http4s" %% "http4s-ember-server" % "0.23.30",
      "org.http4s" %% "http4s-ember-client" % "0.23.30",
      "io.circe" %% "circe-core" % "0.14.14",
      "io.circe" %% "circe-generic" % "0.14.14",
      "io.circe" %% "circe-parser" % "0.14.14",
      "com.github.pureconfig" %% "pureconfig-core" % "0.17.9",
      "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.9",
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "org.typelevel" %% "log4cats-slf4j" % "2.7.0",

      "com.softwaremill.sttp.client4" %% "testing" % "4.0.9" % Test
    ),
    Test / parallelExecution := false
  )
  .dependsOn(`chatops4s-core`)

lazy val `chatops4s-examples` = (project in file("modules/chatops4s-examples"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(

      "com.github.pureconfig" %% "pureconfig-core" % "0.17.9",
      "com.github.pureconfig" %% "pureconfig-cats-effect" % "0.17.9",
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "org.typelevel" %% "log4cats-slf4j" % "2.7.0"
    ),
    Test / parallelExecution := false,
    publish / skip := true,
    run / fork := true,
    run / javaOptions += "-Xmx512m"
  )
  .dependsOn(`chatops4s-core`, `chatops4s-slack`)

lazy val commonSettings = Seq(
  scalaVersion := "3.3.6",
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Wunused:all"
  ),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    "org.typelevel" %% "cats-effect-testing-scalatest" % "1.6.0" % Test
  ),
  organization := "com.chatops4s",
  homepage := Some(url("https://business4s.github.io/chatop4s/")),
  licenses := List(License.MIT),
  developers := List(

      Developer(
        "Krever",
        "Voytek Pituła",
        "w.pitula@gmail.com",
        url("https://v.pitula.me"),
      ),
    Developer(
      "masterhj",
      "Himanshu Jaiswal",
      "jaiswalhiman1410@gmail.com",
      url("https://github.com/masterhj")
    ),
  ),
  versionScheme := Some("semver-spec"),
  version := "0.1.0-SNAPSHOT"
)