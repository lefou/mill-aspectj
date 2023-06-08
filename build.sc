// plugins
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest::0.7.0`
import $ivy.`de.tototec::de.tobiasroeser.mill.vcs.version::0.3.1`

// imports
import mill._
import mill.define.{Command, Module, Sources, Target, TaskModule}
import mill.scalalib._
import mill.scalalib.publish._
import de.tobiasroeser.mill.integrationtest._
import de.tobiasroeser.mill.vcs.version.VcsVersion
import os.Path
import scala.util.Properties

val baseDir = build.millSourcePath
val rtMillVersion = build.version

trait Deps {
  def millPlatform: String
  def millVersion: String
  def scalaVersion: String = "2.13.11"
  def itestVersions: Seq[String]

  val aspectjTools = ivy"org.aspectj:aspectjtools:1.9.5"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  val millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  val millMainApi = ivy"com.lihaoyi::mill-main-api:${millVersion}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val millScalalibApi = ivy"com.lihaoyi::mill-scalalib-api:${millVersion}"
  val scalaTest = ivy"org.scalatest::scalatest:3.2.16"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.25"
}

object Deps_0_11 extends Deps {
  override def millPlatform = millVersion // only valid for exact milestone versions
  override def millVersion = "0.11.0" // select only exact milestones
  override def itestVersions = Seq(millVersion)
}
object Deps_0_10 extends Deps {
  override def millPlatform = "0.10"
  override def millVersion = "0.10.0" // scala-steward:off
  override def itestVersions = Seq("0.10.11", millVersion)
}
object Deps_0_9 extends Deps {
  override def millPlatform = "0.9"
  override def millVersion = "0.9.3" // scala-steward:off
  // 0.9.5 is the first version that has inner `JavaModule.JavaModuleTests` traits
  override def itestVersions = Seq("0.9.12", "0.9.5")
}

val configs = Seq(Deps_0_11, Deps_0_10, Deps_0_9)
val matrix = configs.map(d => d.millPlatform -> d).toMap
val testMatrix = configs.flatMap(d => d.itestVersions.map(_ -> d)).toMap

trait MillAjcModule extends CrossScalaModule with PublishModule {
  def millPlatform: String
  def deps: Deps = matrix(millPlatform)
  def crossScalaVersion = deps.scalaVersion
  override def sources: Sources = T.sources(
    millSourcePath / "src",
    if (Seq(Deps_0_9, Deps_0_10).forall(d => d.millPlatform != millPlatform))
      millSourcePath / s"src-${millPlatform.split("[.]").take(2).mkString(".")}"
    else
      millSourcePath / s"src-0.10-"
  )

  override def ivyDeps = T {
    Agg(ivy"${scalaOrganization()}:scala-library:${crossScalaVersion}")
  }

  override def artifactSuffix = s"_mill${millPlatform}_${artifactScalaVersion()}"
  override def publishVersion: T[String] = VcsVersion.vcsState().format()

  override def javacOptions = {
    (if (Properties.isJavaAtLeast(8)) Seq("-release", "8")
     else Seq("-source", "1.8", "-target", "1.8")) ++
      Seq("-encoding", "UTF-8", "-deprecation")
  }

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

  override def skipIdea: Boolean = deps != configs.head
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

  def versionsFile: T[PathRef] = T {
    val workerStr = s"${worker(
        millPlatform
      ).pomSettings().organization}:${worker(millPlatform).artifactId()}:${worker(
        millPlatform
      ).publishVersion()}"
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
         |  val millAspectjWorkerImplIvyDep = "${workerStr}"
         |}
         |""".stripMargin

    os.write(T.dest / "Versions.scala", body)
    PathRef(T.dest)
  }

  override def generatedSources: T[Seq[PathRef]] = T {
    super.generatedSources() ++ Seq(versionsFile())
  }

  object test extends Tests with TestModule.ScalaTest {
    override def ivyDeps = Agg(
      deps.scalaTest
    )
  }
}

val testVersions = configs.flatMap(_.itestVersions)

object itest extends Cross[ItestCross](testVersions: _*) with TaskModule {
  def defaultCommandName(): String = "test"
  def testCached: T[Seq[TestCase]] = itest(testVersions.head).testCached
  def test(args: String*): Command[Seq[TestCase]] = itest(testVersions.head).test(args: _*)
}
class ItestCross(millVersion: String) extends MillIntegrationTestModule {
  val deps: Deps = testMatrix(millVersion)
  val millPlatform = deps.millPlatform
  override def millSourcePath: Path = super.millSourcePath / os.up
  override def millTestVersion = millVersion
  override def pluginsUnderTest = Seq(aspectj(millPlatform))
  override def temporaryIvyModules = Seq(api(millPlatform), worker(millPlatform))

  override def testTargets: T[Seq[String]] = Seq("--color", "false", "verify")
  override def testCases = T {
    super.testCases().filter { tc =>
      val versionPrefix = millVersion.split("[.]").take(2).mkString(".")
      if (tc.path.last == "scala+ajc" && Seq("0.6", "0.7", "0.8", "0.9").contains(versionPrefix)) {
        T.log.errorStream.println(
          s"Skipping test '${tc.path.last}' for Mill version ${mill.BuildInfo.millVersion} < 0.10.0"
        )
        false
      } else true
    }
  }
}

/** Some convenience targets. */
object P extends Module {

  /**
   * Update the millw script.
   */
  def millw() = T.command {
    val target =
      mill.modules.Util.download("https://raw.githubusercontent.com/lefou/millw/master/millw")
    val millw = baseDir / "millw"
    val res = os.proc(
      "sed",
      s"""s,\\(^DEFAULT_MILL_VERSION=\\).*$$,\\1${scala.util.matching.Regex.quoteReplacement(
          rtMillVersion()
        )},""",
      target.path.toIO.getAbsolutePath()
    ).call(cwd = baseDir)
    os.write.over(millw, res.out.text())
    os.perms.set(millw, os.perms(millw) + java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
    target
  }
}
