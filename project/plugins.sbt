resolvers ++= Seq(
  Classpaths.typesafeResolver, 
  "jgit-repo" at "http://download.eclipse.org/jgit/maven",
  "sonatype-releases" at "http://oss.sonatype.org/content/repositories/releases"
)


// https://github.com/sbt/sbt-multi-jvm
// Original 0.3.5
// Latest 0.3.11 (requires sbt 0.12
// these comment markers are for including code into the docs
//#sbt-multi-jvm
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.11")
//#sbt-multi-jvm

// https://github.com/sbt/sbt-scalariform
// Original 1.0.0
// Use 1.0.1 for sbt 0.12 
// Use 1.3.0 for sbt 0.13
addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

// https://github.com/sbt/sbt-ghpages
// Original 0.5.0
// Latest 0.5.3
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.3")

// https://github.com/sritchie/sbt-gitflow
// Only works on sbt 0.12
// addSbtPlugin("com.twitter" % "sbt-gitflow" % "0.1.0")


// https://github.com/typesafehub/migration-manager/wiki/Sbt-plugin
// Original 0.1.5
// Latest 0.1.7
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.7")


// https://github.com/softprops/ls-sbt
addSbtPlugin("me.lessis" % "ls-sbt" % "0.1.2")

//addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.6.2")

//addSbtPlugin("com.typesafe.sbtosgi" % "sbtosgi" % "0.3.0")
