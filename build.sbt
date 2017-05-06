name := "opentracing-akka"

organization := "io.opentracing.contrib.akka"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.12.2"

updateOptions := updateOptions.value.withLatestSnapshots(false)

resolvers += "Lightbend Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"       % "2.5.0",
  "com.typesafe.akka" %% "akka-remote"      % "2.5.0",
  "io.opentracing"    %  "opentracing-api"  % "0.21.1-SNAPSHOT",
  "io.opentracing"    %  "opentracing-mock" % "0.21.1-SNAPSHOT" % "test",
  "org.scalatest"     %% "scalatest"        % "3.0.1"  % "test"
)
