lazy val `chatops4s` = (project in file("."))
  .settings(commonSettings)
  .aggregate(
    `chatops4s-discord`,
    `chatops4s-discord-example`,
  )

lazy val `chatops4s-discord-example` = (project in file("chatops4s-discord-example"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"    % "1.11.36",
      "org.http4s" %% "http4s-blaze-server"    % "0.23.17",
      "com.softwaremill.sttp.client4" %% "cats"                 % "4.0.9",
    ),
    Test / parallelExecution := false,
  ).dependsOn(`chatops4s-discord`)

lazy val `chatops4s-discord` = (project in file("chatops4s-discord"))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.softwaremill.sttp.tapir" %% "tapir-core"             % "1.11.36",
      "com.softwaremill.sttp.tapir" %% "tapir-cats"             % "1.11.36",
      "org.bouncycastle" % "bcpkix-jdk15on"                     % "1.70",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"       % "1.11.36",
      "com.softwaremill.sttp.client4" %% "core"                 % "4.0.9",
      "com.softwaremill.sttp.client4" %% "circe"                % "4.0.9",
      "io.circe" %% "circe-parser"                              % "0.14.14",
      "org.typelevel" %% "cats-effect"                          % "3.6.2",
      "com.typesafe.scala-logging" %% "scala-logging"           % "3.9.5"
    ),
    Test / parallelExecution := false,
  )

lazy val commonSettings = Seq(
  scalaVersion      := "3.7.1",
  scalacOptions ++= Seq("-no-indent", "-Xmax-inlines", "64", "-explain-cyclic", "-Ydebug-cyclic"),
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  ),
  organization      := "org.business4s",
  homepage          := Some(url("https://business4s.github.io/chatop4s/")),
  licenses          := List(License.MIT),
  developers        := List(
    Developer(
      "Krever",
      "Voytek Pituła",
      "w.pitula@gmail.com",
      url("https://v.pitula.me"),
    ),
    Developer(
      "Liam Grossman",
      "Liam Grossman",
      "me@liamgrossman.com",
      url("https://liamgrossman.com"),
    ),

  ),
  versionScheme     := Some("semver-spec"),
)