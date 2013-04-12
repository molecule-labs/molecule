package molecule

import sbt._

object Repos {
  val sonatypeNexusSnapshots = "Sonatype Nexus Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  val sonatypeNexusReleases = "Sonatype Nexus Releases" at "https://oss.sonatype.org/content/repositories/releases"
  val sonatypeNexusStaging = "Sonatype Nexus Staging" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  val fuseSourceSnapshots = "FuseSource Snapshots" at "http://repo.fusesource.com/nexus/content/repositories/snapshots"
}
