import org.typelevel.scalacoptions.ScalacOptions

lazy val `chatops4s` = (project in file("."))
  .settings(commonSettings)
  .settings(
    publish / skip := true,
  )
  .aggregate(
    `chatops4s-slack-client`,
    `chatops4s-slack`,
    `chatops4s-examples`,
  )

lazy val `chatops4s-slack-client` = (project in file("chatops4s-slack-client"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.circe"                      %% "circe-core"    % "0.14.14",
      "io.circe"                      %% "circe-generic" % "0.14.14",
      "io.circe"                      %% "circe-parser"  % "0.14.14",
      "com.softwaremill.sttp.client4" %% "core"          % "4.0.9",
      "com.softwaremill.sttp.client4" %% "circe"         % "4.0.9",
    ),
  )

lazy val `chatops4s-slack` = (project in file("chatops4s-slack"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j"                      % "slf4j-api"   % "2.0.17",
      "org.typelevel"                 %% "cats-effect" % "3.6.3" % Test,
      "com.softwaremill.sttp.client4" %% "cats"        % "4.0.9" % Test,
    ),
    Test / parallelExecution := false,
  )
  .dependsOn(`chatops4s-slack-client`)

lazy val `chatops4s-examples` = (project in file("chatops4s-examples"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"                 %% "cats-effect"     % "3.6.3",
      "com.softwaremill.sttp.client4" %% "fs2"             % "4.0.9",
      "ch.qos.logback"                 % "logback-classic" % "1.5.32",
    ),
    Test / parallelExecution := false,
    publish / skip           := true,
    run / fork               := true,
  )
  .dependsOn(`chatops4s-slack`)

lazy val commonSettings = Seq(
  scalaVersion  := "3.7.1",
  scalacOptions ++= Seq(
    "-no-indent",
    "-Xmax-inlines",
    "64",
    "-explain-cyclic",
    "-Ydebug-cyclic",
  ),
  Test / tpolecatExcludeOptions ++= Set(
    ScalacOptions.warnValueDiscard,
    ScalacOptions.warnNonUnitStatement,
  ),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest"                     % "3.2.19" % Test,
    "org.typelevel" %% "cats-effect-testing-scalatest" % "1.6.0"  % Test,
  ),
  organization  := "org.business4s",
  homepage      := Some(url("https://business4s.github.io/chatops4s/")),
  licenses      := List(License.MIT),
  developers    := List(
    Developer(
      "Krever",
      "Voytek Pituła",
      "w.pitula@gmail.com",
      url("https://v.pitula.me"),
    ),
  ),
  versionScheme := Some("semver-spec"),
  version       := "0.1.0-SNAPSHOT",
)
