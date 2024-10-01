lazy val root = project
  .in(file("."))
  .settings(
    name         := "designer",
    version      := "0.0.1",
    scalaVersion := Versions.scala3Version,
    libraryDependencies ++= Dependencies.all,
    scalacOptions ++= Seq("-Ymacro-annotations", "-Yretain-trees")
  )

resolvers ++= Resolver. sonatypeOssRepos("snapshots")