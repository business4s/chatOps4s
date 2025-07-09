ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.3.6"

ThisBuild / organization := "org.business4s"

lazy val root = (project in file("."))
  .settings(
    name := "chatOps4s",
  )