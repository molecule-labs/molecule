package molecule

import sbt._

object Dependencies {
  
  object Test {
    type MM = String => ModuleID
	
    // Sort by artifact ID.
    lazy val junit = "junit" % "junit" % "4.11"
    lazy val specs: MM = sv => "org.scala-tools.testing" % "specs" % specsVersion(sv) cross specsCross
    //lazy val specs2: MM = sv => "org.specs2" %% "specs2" % specs2Version(sv)
    lazy val specs2: MM = sv => "org.specs2" % "specs2_2.9.2" % specs2Version(sv)
	lazy val scalatest: MM = sv => "org.scalatest" %% "scalatest" % scalatestVersion(sv) % "test"
	  
    private val scalatestVersion: String => String = {
      case sv if sv startsWith "2.8." => "1.8"
      case _ => "1.9.1"
    }

    private val specsCross = CrossVersion.binaryMapped {
      case "2.8.2" => "2.8.1" // _2.8.2 published with bad checksum
      case "2.9.2" => "2.9.1"
      case "2.10.0" => "2.10" // sbt bug?
      case bin => bin
    }
    private val specsVersion: String => String = {
      case sv if sv startsWith "2.8." => "1.6.8"
      case "2.9.0-1" => "1.6.8"
      case _ => "1.6.9"
    }

    private val specs2Version: String => String = {
      case sv if sv startsWith "2.8." => "1.5"
      case "2.9.0-1" => "1.8.2"
      case sv if sv startsWith "2.9." => "1.12.3"
      case _ => "1.13"
    }
  }
	
  object Compilation {
	// Compiler plugins
    val genjavadoc = compilerPlugin("com.typesafe.genjavadoc" %% "genjavadoc-plugin" % "0.3" cross CrossVersion.full) // ApacheV2
	
	lazy val mbench = "com.github.sbocq" %% "mbench" % "0.2"
  }
}