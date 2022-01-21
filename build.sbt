ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.7"

lazy val root = (project in file("."))
  .settings(
    name := "amqp-reconnect"
  )
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)

val AkkaVersion = "2.6.14"

val circeVersion = "0.14.1"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-generic-extras"
).map(_ % circeVersion)

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.10",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
  "com.lightbend.akka" %% "akka-stream-alpakka-amqp" % "3.0.4",
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "org.testcontainers" % "rabbitmq" % "1.16.2" % "it",
  "org.scalatest" %% "scalatest" % "3.2.10" % "it"

)