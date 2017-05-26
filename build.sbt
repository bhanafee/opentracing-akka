name := "opentracing-akka"

organization := "io.opentracing.contrib.akka"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.12.2"

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"       % "2.5.1",
  "com.typesafe.akka" %% "akka-remote"      % "2.5.1",
  "io.opentracing"    %  "opentracing-api"  % "0.30.1.RC3-SNAPSHOT",
  "io.opentracing"    %  "opentracing-mock" % "0.30.1.RC3-SNAPSHOT" % "test",
  "org.scalatest"     %% "scalatest"        % "3.0.1"  % "test"
)
