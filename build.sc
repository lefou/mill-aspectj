import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest_mill0.9:0.4.0-5-9dce73`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version_mill0.9:0.1.0`
import mill._
import mill.define.{Command, Module, Target, TaskModule}
import mill.scalalib._
import mill.scalalib.publish._
import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version.VcsVersion
import os.Path

val baseDir = build.millSourcePath
val rtMillVersion = build.version

trait Deps {
  def millPlatform: String
  def millVersion: String
  def scalaVersion: String
  def itestVersions: Seq[String]

  val aspectjTools = ivy"org.aspectj:aspectjtools:1.9.5"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  val millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  val millMainApi = ivy"com.lihaoyi::mill-main-api:${millVersion}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val millScalalibApi = ivy"com.lihaoyi::mill-scalalib-api:${millVersion}"
  val scalaTest = ivy"org.scalatest::scalatest:3.2.9"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.25"
}


object Deps_0_9 extends Deps {
  override def millPlatform = "0.9"
  override def millVersion = "0.9.3"
  override def scalaVersion = "2.13.5"
  override def itestVersions = Seq("0.9.6", "0.9.5", "0.9.4", "0.9.3")
}
object Deps_0_7 extends Deps {
  override def millPlatform = "0.7"
  override def millVersion = "0.7.0"
  override def scalaVersion = "2.13.5"
  override def itestVersions = Seq("0.8.0", "0.7.3", "0.7.2", "0.7.1", "0.7.0")
}
object Deps_0_6 extends Deps {
  override def millPlatform = "0.6"
  override def millVersion = "0.6.0"
  override def scalaVersion = "2.12.13"
  override def itestVersions = Seq("0.6.3", "0.6.2", "0.6.1", "0.6.0")
}


val configs = Seq(Deps_0_9,Deps_0_7,Deps_0_6)
val matrix = configs.map(d => d.millPlatform -> d).toMap
val testMatrix = configs.flatMap(d => d.itestVersions.map(_ -> d)).toMap

trait MillAjcModule extends CrossScalaModule with PublishModule {
  def millPlatform: String
  def deps: Deps = matrix(millPlatform)
  def crossScalaVersion = deps.scalaVersion

  override def ivyDeps = T {
    Agg(ivy"${scalaOrganization()}:scala-library:${crossScalaVersion}")
  }

  override def artifactSuffix = s"_mill${millPlatform}_${artifactScalaVersion()}"
  override def publishVersion: T[String] = VcsVersion.vcsState().format()

  override def javacOptions = Seq("-source", "1.8", "-target", "1.8")
  override def scalacOptions = Seq("-target:jvm-1.8")

  def pomSettings = T {
    PomSettings(
      description = "AspectJ compiler support for mill",
      organization = "de.tototec",
      url = "https://github.com/lefou/mill-aspectj",
      licenses = Seq(License.`Apache-2.0`),
      versionControl = VersionControl.github("lefou", "mill-aspectj"),
      developers = Seq(Developer("lefou", "Tobias Roeser", "https.//github.com/lefou"))
    )
  }
}


object api extends Cross[ApiCross](configs.map(_.millPlatform): _*)
class ApiCross(override val millPlatform: String) extends MillAjcModule {
  override def artifactName = "de.tobiasroeser.mill.aspectj-api"
  override def compileIvyDeps: T[Agg[Dep]] = Agg(
    deps.millMainApi,
    deps.millScalalibApi
  )

}

object worker extends Cross[WorkerCross](configs.map(_.millPlatform): _*)
class WorkerCross(override val millPlatform: String) extends MillAjcModule {
  override def artifactName: T[String] = "de.tobiasroeser.mill.aspectj-worker"
  override def moduleDeps: Seq[PublishModule] = Seq(api(millPlatform))
  override def compileIvyDeps = Agg(
    deps.millMainApi,
    deps.millScalalibApi,
    deps.aspectjTools
  )
}

object aspectj extends Cross[AspectjCross](configs.map(_.millPlatform): _*)
class AspectjCross(override val millPlatform: String) extends MillAjcModule {
  override def artifactName = "de.tobiasroeser.mill.aspectj"
  override def moduleDeps: Seq[PublishModule] = Seq(api(millPlatform))
  override def compileIvyDeps = Agg(
    deps.millMain,
    deps.millScalalib
  )

  object test extends Tests {
    override def ivyDeps = Agg(
      deps.scalaTest
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }

  override def generatedSources: T[Seq[PathRef]] = T{
    super.generatedSources() ++ {
      val dest = T.ctx().dest
      val body =
        s"""package de.tobiasroeser.mill.aspectj
           |
           |/**
           | * Build-time generated versions file.
           | */
           |object Versions {
           |  /** The mill-aspectj version. */
           |  val millAspectjVersion = "${publishVersion()}"
           |  /** The mill API version used to build mill-kotlin. */
           |  val buildTimeMillVersion = "${deps.millVersion}"
           |  /** The ivy dependency holding the mill aspectj worker impl. */
           |  val millAspectjWorkerImplIvyDep = "${worker(millPlatform).pomSettings().organization}:${worker(millPlatform).artifactId()}:${worker(millPlatform).publishVersion()}"
           |}
           |""".stripMargin

      os.write(dest / "Versions.scala", body)

      Seq(PathRef(dest))
    }
  }
}


object itest extends Cross[ItestCross](configs.flatMap(_.itestVersions): _*)
class ItestCross(millVersion: String)  extends MillIntegrationTestModule {
  val deps: Deps = testMatrix(millVersion)
  val millPlatform =  deps.millPlatform
  override def millSourcePath: Path = super.millSourcePath / os.up
  override def millTestVersion = millVersion
  override def pluginsUnderTest = Seq(aspectj(millPlatform))
  override def temporaryIvyModules = Seq(api(millPlatform), worker(millPlatform))

  override def testTargets: T[Seq[String]] = Seq("--color", "false", "verify")

}

/** Some convenience targets. */
object P extends Module {
    /**
     * Update the millw script.
     */
    def millw() = T.command {
      val target = mill.modules.Util.download("https://raw.githubusercontent.com/lefou/millw/master/millw")
      val millw = baseDir / "millw"
      val res = os.proc(
        "sed", s"""s,\\(^DEFAULT_MILL_VERSION=\\).*$$,\\1${scala.util.matching.Regex.quoteReplacement(rtMillVersion())},""",
        target.path.toIO.getAbsolutePath()).call(cwd = baseDir)
      os.write.over(millw, res.out.text())
      os.perms.set(millw, os.perms(millw) + java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
      target
    }
}
