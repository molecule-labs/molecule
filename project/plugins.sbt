resolvers ++= Seq(
  Classpaths.typesafeResolver, 
  "jgit-repo" at "http://download.eclipse.org/jgit/maven",
  "sonatype-releases" at "http://oss.sonatype.org/content/repositories/releases"
)

// these comment markers are for including code into the docs
//#sbt-multi-jvm
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.5")
//#sbt-multi-jvm

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.0")

addSbtPlugin("com.twitter" % "sbt-gitflow" % "0.1.0")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.5")

addSbtPlugin("me.lessis" % "ls-sbt" % "0.1.2")

//addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.6.2")

//addSbtPlugin("com.typesafe.sbtosgi" % "sbtosgi" % "0.3.0")
