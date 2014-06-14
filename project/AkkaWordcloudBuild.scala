import sbt._
import Keys._

object AkkaWordcloudBuild extends Build {

  lazy val wordcloudProject = Project(id = "akka-wordcloud", base = file("."),
    settings = Project.defaultSettings ++ Seq(
      name := "akka-wordcloud",
      version := "1.0",
      resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
      resolvers += "Twitter4j Repository" at "http://twitter4j.org/maven2",
      resolvers += "Spray repository" at "http://repo.spray.io",
      scalaVersion := "2.10.3",
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.3.0",
        "com.typesafe.akka" %% "akka-testkit" % "2.3.0",
        "com.typesafe.akka" %% "akka-remote" % "2.3.0",
        "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.0",
        "joda-time" % "joda-time" % "2.3",
        "org.joda" % "joda-convert" % "1.6",
        "org.twitter4j" % "twitter4j-core" % "4.0.1",
        "org.twitter4j" % "twitter4j-async" % "4.0.1",
        "org.twitter4j" % "twitter4j-stream" % "4.0.1",
        "org.twitter4j" % "twitter4j-media-support" % "4.0.1",
        "io.spray" % "spray-can" % "1.3.1",
        "io.spray" % "spray-http" % "1.3.1",
        "io.spray" % "spray-routing" % "1.3.1",
        "org.json4s" %% "json4s-native" % "3.2.9"
      )
    ))
}
