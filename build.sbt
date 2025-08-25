
lazy val `chatops4s` = (project in file("."))
  .settings(commonSettings)
  .settings(
    publish / skip := true,
  )
  .aggregate(
    `chatops4s-core`,
    `chatops4s-slack`,
    `chatops4s-examples`,
    `chatops4s-discord`,
  )

lazy val `chatops4s-core` = (project in file("chatops4s-core"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      // TODO remove cats dependency (generalize for F[_])
      "org.typelevel" %% "cats-effect" % "3.6.2",
      "org.typelevel" %% "cats-core" % "2.13.0",
      "io.circe" %% "circe-core" % "0.14.14",
      "io.circe" %% "circe-generic" % "0.14.14",
      "io.circe" %% "circe-parser"  % "0.14.14",
    ),
    Test / parallelExecution := false,
  )

lazy val `chatops4s-slack` = (project in file("chatops4s-slack"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.client4" %% "core"                   % "4.0.9",
      "com.softwaremill.sttp.client4" %% "circe"                  % "4.0.9",
      // TODO we would like to be independent of concrete backend and cats
      "com.softwaremill.sttp.client4" %% "cats"                   % "4.0.9",
      "ch.qos.logback"                 % "logback-classic"        % "1.5.18",
      // TODO replace with scala-logging - its more minimal and currently used by libs in the business4s ecosystem
      "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.5",
    ),
    // TODO remove if possible
    Test / parallelExecution := false,
  )
  .dependsOn(`chatops4s-core`)

lazy val `chatops4s-examples` = (project in file("chatops4s-examples"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.github.pureconfig"       %% "pureconfig-cats-effect"    % "0.17.9",
      "com.github.pureconfig"       %% "pureconfig-generic-scala3" % "0.17.9",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"       % "1.11.38",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"          % "1.11.38",
      "org.http4s"                  %% "http4s-ember-server"       % "0.23.30",
      "ch.qos.logback"               % "logback-classic"           % "1.5.18",
      "com.softwaremill.sttp.client4" %% "cats"                % "4.0.9",
    ),
    // TODO remove if possible
    Test / parallelExecution := false,
    publish / skip           := true,
    run / fork               := true,
    run / javaOptions += "-Xmx512m",
  )
  .dependsOn(`chatops4s-slack`,`chatops4s-discord`)


lazy val `chatops4s-discord` = (project in file("chatops4s-discord"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir"   %% "tapir-core"       % "1.11.38",
      // TODO remove
      "com.softwaremill.sttp.tapir"   %% "tapir-cats"       % "1.11.38",
      "org.bouncycastle"               % "bcpkix-jdk15on"   % "1.70",
      "com.softwaremill.sttp.tapir"   %% "tapir-json-circe" % "1.11.38",
      "com.softwaremill.sttp.client4" %% "core"             % "4.0.9",
      "com.softwaremill.sttp.client4" %% "circe"            % "4.0.9",
      "io.circe"                      %% "circe-parser"     % "0.14.14",
      // TODO remove
      "org.typelevel"                 %% "cats-effect"      % "3.6.2",
      "com.typesafe.scala-logging"    %% "scala-logging"    % "3.9.5",
    ),
  )
  .dependsOn(`chatops4s-core`)

lazy val commonSettings = Seq(
  scalaVersion         := "3.7.1",

  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Wunused:all",
    "-no-indent",
    "-Xmax-inlines",
    "64",
    "-explain-cyclic",
    "-Ydebug-cyclic",
  ),
  Test / scalacOptions := (Test / scalacOptions).value.filterNot(_ == "-Wunused:all"),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest"                     % "3.2.19" % Test,
    "org.typelevel" %% "cats-effect-testing-scalatest" % "1.6.0"  % Test,
  ),
  organization         := "org.business4s",
  homepage             := Some(url("https://business4s.github.io/chatop4s/")),
  licenses             := List(License.MIT),
  developers           := List(
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
      url("https://hjdev-phi.vercel.app"),
    ),
    Developer(
      "Liam Grossman",
      "Liam Grossman",
      "me@liamgrossman.com",
      url("https://liamgrossman.com"),
    ),
  ),
  versionScheme        := Some("semver-spec"),
  version              := "0.1.0-SNAPSHOT",
)
