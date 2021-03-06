import sbt._
import sbt.Keys._

object AppBuild extends Build {

  lazy val project = Project("gyotaku", file("."), settings = mainSettings)

  lazy val mainSettings: Seq[Project.Setting[_]] = Defaults.defaultSettings ++ Seq(
    organization := "com.github.seratch",
    name := "gyotaku",
    version := "0.1.5",
    scalaVersion := "2.9.1",
    externalResolvers ~= (_.filter(_.name != "Scala-Tools Maven2 Repository")),
    resolvers += "twitter" at "http://maven.twttr.com/",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-swing" % "2.9.1",
      "com.twitter" %% "util-eval" % "4.0.1",
      "commons-io" % "commons-io" % "2.3",
      "org.yaml" % "snakeyaml" % "1.10",
      "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.2",
      "org.seleniumhq.selenium" % "selenium-firefox-driver" % "2.22.0",
      "org.scalatest" %% "scalatest" % "1.7.2" % "test"
    ),
    scalacOptions ++= Seq("-deprecation", "-unchecked")
  )

}

