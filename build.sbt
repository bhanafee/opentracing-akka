name := "opentracing-akka"

version := "0.0.1"

scalaVersion := "2.12.1"

resolvers += "Lightbend Repository" at "http://repo.typesafe.com/typesafe/releases/"

scalacOptions += "-deprecation"

scalacOptions += "-unchecked"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"       % "2.5.0",
  "com.typesafe.akka" %% "akka-remote"      % "2.5.0",
  "io.opentracing"    %  "opentracing-api"  % "0.21.0",
  "io.opentracing"    %  "opentracing-mock" % "0.21.0" % "test",
  "org.scalatest"     %% "scalatest"        % "3.0.1"  % "test"
)
