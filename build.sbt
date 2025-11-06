name := """ept"""
organization := "eu.ehri.project"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.13.16"

libraryDependencies += guice
libraryDependencies += jdbc
libraryDependencies += evolutions
libraryDependencies += ws
libraryDependencies += caffeine

// Anorm DB lib
libraryDependencies += "org.playframework.anorm" %% "anorm" % "2.8.1"
libraryDependencies += "org.playframework.anorm" %% "anorm-postgres" % "2.8.1"

// Play JSON lib
libraryDependencies += "org.playframework" %% "play-json" % "3.0.4"

// Site previews
libraryDependencies += "org.jsoup" % "jsoup" % "1.16.1"

// Testing
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
libraryDependencies += "de.leanovate.play-mockws" %% "play-mockws-3-0" % "3.0.1" % Test

// Set the application config to test.conf
Test / javaOptions ++= Seq(
  s"-Dconfig.file=${(LocalRootProject / baseDirectory).value / "conf" / "test.conf"}",
)

// These resources are excluded from the build because they typically
// contain access keys and secrets.
val excludedResources = Seq(
  "doi.conf",
  "clients.conf"
)

// Filter out excluded resources from packaging
Universal / mappings := (Universal / mappings).value.filterNot { case (f, s) =>
  excludedResources contains f.getName
}

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "eu.ehri.project.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "eu.ehri.project.binders._"

// In the build artefact, don't include the app name
topLevelDirectory := None