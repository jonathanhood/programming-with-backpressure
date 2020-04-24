lazy val root = (project in file("."))
  .settings(
    name := "programming-with-backpressure",
    scalaVersion := "2.13.1",
    libraryDependencies += "io.monix" %% "monix" % "3.1.0",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.1"
  )