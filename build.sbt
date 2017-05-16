name := "opentracing-akka"

organization := "io.opentracing.contrib.akka"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"       % "2.5.1",
  "com.typesafe.akka" %% "akka-remote"      % "2.5.1",
  "io.opentracing"    %  "opentracing-api"  % "0.22.0",
  "io.opentracing"    %  "opentracing-mock" % "0.22.0" % "test",
  "org.scalatest"     %% "scalatest"        % "3.0.1"  % "test"
)
