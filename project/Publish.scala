package molecule

import sbt._
import sbt.Keys._
import scala.xml._
import sbtgitflow.ReleasePlugin
import Repos.{sonatypeNexusSnapshots, sonatypeNexusStaging}

object Publish {

  val settings = ReleasePlugin.releaseSettings ++ Seq(

    publishMavenStyle := true,

    publishArtifact in Test := false,

    pomIncludeRepository := { x => false },
	
    publishTo <<= (version) { version: String =>
      if (version.trim.endsWith("SNAPSHOT"))
        Some(sonatypeNexusSnapshots)
      else
        Some(sonatypeNexusStaging)
    },

    pomExtra := (
      <url>https://github.com/sbocq/molecule</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
          <comments>A business-friendly OSS license</comments>
        </license>
      </licenses>
      <scm>
        <url>http://github.com/sbocq/molecule</url>
        <connection>scm:git:git://github.com/sbocq/mbench.git</connection>
      </scm>
      <developers>
        <developer>
          <id>sbocq</id>
          <name>Sébastien Bocq</name>
          <url>https://github.com/sbocq/</url>
        </developer>
        <developer>
          <id>daenenk</id>
          <name>Koen Daenen</name>
          <url>https://github.com/daenenk/</url>
        </developer>
      </developers>
    )
  )

}