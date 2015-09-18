package molecule

import sbt._
import sbt.Keys._
import sbt.Project.Initialize
import scala.util.matching.Regex

object Compiler {

  import Keys._
  
  lazy val settings:Seq[Setting[_]] = Seq(
    crossScalaVersions := Seq( "2.11.7",  "2.9.3", "2.10.5" ),
    // scalaVersion       <<= crossScalaVersions(_.head),
    scalaVersion       := "2.12.0-M2", 
    javaVersion        := alignJavaVersion( scalaVersion.value ),
    javacOptions       <++= javaVersion.map( selectJavacOptions ),
    scalacOptions      ++= selectScalacOptions(javaVersion.value, scalaVersion.value),
    target             := updateTarget(target.value, javaVersion.value), 
    javaHomeBuild      := System.getProperty("java.version")   
  )
  
  object Keys {
  
    val javaHomeBuild = SettingKey[String](
      "java-home-build",
      "The Java installation running the build;\n" +
      "\teither specified by the system environment variable JAVA_HOME,\n" +
      "\tor by the sbt command line option -java-home."
    )
  
    val javaVersion = SettingKey[String](
      "java-version", 
      "Specifies a java version used for java source compliance, javac target and scalac target.\n" +
      "\te.g. Some(\"1.7\")\n" + 
      "\tIt is recommended that the selected javaVersion corresponds with the JDK used to launch sbt.\n" +
      "\tUse the sbt command line option -java-home to specify a JDK different than JAVA_HOME"
    )
    
    val versionXYZ = """.*(\d+)\.(\d+)\.(\d+).*""".r 
}

  
  private def alignJavaVersion(scalaVersion: String): String = scalaVersion match { 
     case versionXYZ("2","9",_) => "1.6"
     case versionXYZ("2","10",_) => "1.6"
     case versionXYZ("2","11",_) => "1.6"
     case versionXYZ("2","12",_) => "1.8"
     case s => "1.6"
  }
  
  lazy val scalacCommonOptions = 
    Seq("-optimize", "-unchecked", "-deprecation", "-Xcheckinit", "-encoding", "utf8")
  
  lazy val scalacLangOptions = 
    Seq("-feature", "-language:postfixOps", "-language:implicitConversions",
        "-language:reflectiveCalls", "-language:higherKinds", "-language:existentials")
        
  lazy val scalacInvokeDynamicOptions = 
    Seq("-Ydelambdafy:method", "-Ybackend:GenBCode")
    
  lazy val javacCommonOptions = 
    Seq("-Xlint:deprecation", "-encoding", "utf8")

  def selectJavacOptions(jv: String) = javacCommonOptions ++ 
    (jv match {
      case "" | "1.6" => Seq[String]()
      case jv => Seq("-target", jv, "-source", jv) 
    })
     
  def selectScalacOptions(jv: String, scalaVersion: String) : Seq[String] = {
    val jvmTargetOptions = jv match {
      case "" | "1.6" => Seq[String]()
      case jv => Seq("jvmTarget:jvm-" + jv)
    }
  
    scalaVersion match { 
      case versionXYZ("2","9",_) => scalacCommonOptions ++ jvmTargetOptions
      case versionXYZ("2","10",_) => scalacCommonOptions ++ scalacLangOptions ++ jvmTargetOptions
      case versionXYZ("2","11","7") => 
        scalacCommonOptions ++ scalacLangOptions ++ jvmTargetOptions ++ scalacInvokeDynamicOptions
      case versionXYZ("2","11",_) => scalacCommonOptions ++ scalacLangOptions ++ jvmTargetOptions
      case versionXYZ("2","12", _) => scalacCommonOptions ++ scalacLangOptions // jvm target is always 1.8
      case s =>
        println("No specific scalacOptions specified for scala " + s + " taget, selecting options for scala 2.11.")
        scalacCommonOptions ++ scalacLangOptions ++ jvmTargetOptions
    }
  }
  
  def updateTarget(target: sbt.File, javaVersion: String): sbt.File = javaVersion match {
    case "" | "1.6" => target
    case jv => target / ("jvm-" + jv) 
  }

} 
 

