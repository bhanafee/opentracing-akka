name := "opentracing-akka"

organization := "io.opentracing.contrib.akka"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.12.4"

resolvers += "Bintray" at "http://dl.bintray.com/opentracing/maven"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"       % "2.5.8",
  "com.typesafe.akka" %% "akka-remote"      % "2.5.8",
  "io.opentracing"    %  "opentracing-api"  % "0.30.0",
  "io.opentracing"    %  "opentracing-mock" % "0.30.0" % "test",
  "org.scalatest"     %% "scalatest"        % "3.0.4"  % "test"
)
