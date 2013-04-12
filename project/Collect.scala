package molecule

import sbt._
import sbt.Keys._
import sbt.Project.Initialize

object Collect {

  val collectDirectory = SettingKey[File]("collect-directory", "The directory where artifacts are collected using one of the 'collect-x' tasks.")
	
  lazy val settings:Seq[Setting[_]] = collectSettings ++ Seq(
    collectDirectory <<= (baseDirectory in ThisBuild, scalaVersion){(baseDir, sv) => baseDir / "target" / sv}
  )

  lazy val doNotCollect:Seq[Setting[_]] = Seq(
      (artifact in (Compile, packageBin)) ~= (_.copy(`type` =  "XXX")),
	  (artifact in (Compile, packageDoc)) ~= (_.copy(`type` =  "XXX")),
	  (artifact in (Compile, packageSrc)) ~= (_.copy(`type` =  "XXX"))
  )
  
  //
  // Helpers
  //
  
  private def mkTaskKey(`type`:String):TaskKey[Unit] =
    TaskKey("collect-" + `type`, "Collect '" + `type` + "' artifact into the directory designated by 'collect-dir'") 

  private def mkTaskArtifactPath(parentKey:TaskKey[File]) = (collectDirectory, artifact in (Compile, parentKey))((dstDir, art) => 
    if (art.classifier.isDefined) {
      dstDir / art.classifier.get / (art.name + "-" + art.`type` + "." + art.extension)
	} else
	  dstDir / (art.name + "." + art.extension)
  )
  
  private def mkTaskBody(`type`:String, parentKey:TaskKey[File], collect:TaskKey[Unit]):Initialize[Task[Unit]] = 
    (artifact in (Compile, parentKey), parentKey in Compile, artifactPath in collect, streams) map ((art, srcJar, dstJar, s) => {
	  if (art.`type` == `type`) {
        import s.log
	    IO.copyFile(srcJar, dstJar, true)
	    log.info("Collected " + dstJar)
	  }
  })

  private def mkTaskSettings(parentKey:TaskKey[File], `type`:String):Seq[Setting[_]] = {
    val collect = mkTaskKey(`type`)
    Seq(
      artifactPath in collect <<= mkTaskArtifactPath(parentKey),
	  collect <<= mkTaskBody(`type`, parentKey, collect),
	  cleanFiles <+= artifactPath in collect
    )
  }

  // Generic tasks
    
  private lazy val tasks:Seq[(TaskKey[File], String)] = Seq(
    packageBin -> "jar", 
	packageDoc -> "doc", 
	packageSrc -> "src"
  )

  private lazy val collectSettings = tasks.flatMap(Function.tupled(mkTaskSettings))

}
