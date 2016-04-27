import sbt.Keys._

object Common {
  
  val settings =
    List(
      name := "storedq",
      version := "0.0.1",
      organization := "inu",
      scalaVersion := Version.scala
    )
}