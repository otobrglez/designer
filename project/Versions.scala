import sbt.*

object Versions {
  lazy val scala3Version = "3.5.1"
  lazy val scalaVersion  = "2.13.15" // For reflections
  lazy val zio           = "2.1.9"
  lazy val zioSchema     = "1.5.0"
  lazy val zioPrelude    = "1.0.0-RC31"
}

object Dependencies {
  lazy val all: Seq[ModuleID] = Seq(
    "dev.zio" %% "zio",
    "dev.zio" %% "zio-streams"
  ).map(_ % Versions.zio) ++ Seq(
    "dev.zio" %% "zio-test",
    "dev.zio" %% "zio-test-sbt",
    "dev.zio" %% "zio-test-magnolia"
  ).map(_ % Versions.zio % Test) ++ Seq(
    "org.scalameta" %% "munit" % "1.0.0" % Test
  ) ++ Seq(
    "dev.zio" %% "zio-schema",
    // "dev.zio" %% "zio-schema-avro",
    // "dev.zio" %% "zio-schema-bson",
    "dev.zio" %% "zio-schema-json",
    // "dev.zio" %% "zio-schema-msg-pack",
    "dev.zio" %% "zio-schema-protobuf",
    // "dev.zio" %% "zio-schema-thrift",
    "dev.zio" %% "zio-schema-zio-test",
    "dev.zio" %% "zio-schema-derivation"
  ).map(_ % Versions.zioSchema) ++ Seq(
    // TODO: Should this be via "provided"?
    // "org.scala-lang" % "scala-reflect" % Versions.scalaVersion % "provided"
  ) ++ Seq(
    "dev.zio" %% "zio-prelude" % Versions.zioPrelude
  )
}
