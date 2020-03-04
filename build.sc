import mill._
import mill.define.{Command, Module, TaskModule}
import mill.scalalib._
import mill.scalalib.publish._
import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest:0.2.1`
import de.tobiasroeser.mill.integrationtest._
import mill.api.Loose
import mill.main.Tasks

val baseDir = build.millSourcePath
val rtMillVersion = build.version

object Deps {
  def millVersion = "0.6.0"
  def scalaVersion = "2.12.10"

  val aspectjTools = ivy"org.aspectj:aspectjtools:1.9.5"
  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  val millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  val millMainApi = ivy"com.lihaoyi::mill-main-api:${millVersion}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val millScalalibApi = ivy"com.lihaoyi::mill-scalalib-api:${millVersion}"
  val scalaTest = ivy"org.scalatest::scalatest:3.0.8"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.25"
}

trait MillAjcModule extends ScalaModule with PublishModule {

  def scalaVersion = T { Deps.scalaVersion }

  override def ivyDeps = T {
    Agg(ivy"${scalaOrganization()}:scala-library:${scalaVersion()}")
  }

  def publishVersion = GitSupport.publishVersion()._2

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


object api extends MillAjcModule {
  override def artifactName = T { "de.tobiasroeser.mill.aspectj-api" }
  override def compileIvyDeps: T[Loose.Agg[Dep]] = Agg(
    Deps.millMainApi,
    Deps.millScalalibApi
  )

}

object worker extends MillAjcModule {
  override def artifactName: T[String] = "de.tobiasroeser.mill.aspectj-worker"
  override def moduleDeps: Seq[PublishModule] = Seq(api)
  override def compileIvyDeps = Agg(
    Deps.millMainApi,
    Deps.millScalalibApi,
    Deps.aspectjTools
  )
}

object aspectj extends MillAjcModule {

  override def artifactName = "de.tobiasroeser.mill.aspectj"

  override def moduleDeps: Seq[PublishModule] = Seq(api)

  override def compileIvyDeps = Agg(
    Deps.millMain,
    Deps.millScalalib
  )

  object test extends Tests {
    override def ivyDeps = Agg(
      Deps.scalaTest
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
           |  val buildTimeMillVersion = "${Deps.millVersion}"
           |  /** The ivy dependency holding the mill aspectj worker impl. */
           |  val millAspectjWorkerImplIvyDep = "${worker.pomSettings().organization}:${worker.artifactId()}:${worker.publishVersion()}"
           |}
           |""".stripMargin

      os.write(dest / "Versions.scala", body)

      Seq(PathRef(dest))
    }
  }
}

object itest extends MillIntegrationTestModule {

  def millTestVersion = T {
    val ctx = T.ctx()
    ctx.env.get("TEST_MILL_VERSION").filterNot(_.isEmpty).getOrElse(Deps.millVersion)
  }

  def pluginsUnderTest = Seq(aspectj)
  def temporaryIvyModules = Seq(api, worker)


}

import mill.define.Sources

object GitSupport extends Module {

  /**
   * The current git revision.
   */
  def gitHead: T[String] = T.input {
    sys.env.get("TRAVIS_COMMIT").getOrElse(
      os.proc('git, "rev-parse", "HEAD").call().out.trim
    ).toString()
  }

  /**
   * Calc a publishable version based on git tags and dirty state.
   *
   * @return A tuple of (the latest tag, the calculated version string)
   */
  def publishVersion: T[(String, String)] = T.input {
    val tag =
      try Option(
        os.proc('git, 'describe, "--exact-match", "--tags", "--always", gitHead()).call().out.trim
      )
      catch {
        case e => None
      }

    val dirtySuffix = os.proc('git, 'diff).call().out.string.trim() match {
      case "" => ""
      case s => "-DIRTY" + Integer.toHexString(s.hashCode)
    }

    tag match {
      case Some(t) => (t, t)
      case None =>
        val latestTaggedVersion = os.proc('git, 'describe, "--abbrev=0", "--always", "--tags").call().out.trim

        val commitsSinceLastTag =
          os.proc('git, "rev-list", gitHead(), "--not", latestTaggedVersion, "--count").call().out.trim.toInt

        (latestTaggedVersion, s"$latestTaggedVersion-$commitsSinceLastTag-${gitHead().take(6)}$dirtySuffix")
    }
  }

}

/** Some convenience targets. */
object P extends Module {
    /** Build JARs. */
    def build() = T.command {
      aspectj.jar()
    }

    /** Run tests. */
    def test() = T.command {
      aspectj.test.test()()
      itest.test()()
    }

    def testCached = T{
      aspectj.test.testCached()
      itest.testCached()
    }

    def install() = T.command {
      T.ctx().log.info("Installing")
      testCached()
      api.publishLocal()()
      worker.publishLocal()()
      aspectj.publishLocal()()
    }

    def checkRelease: T[Boolean] = T.input {
      millw()()
      if (GitSupport.publishVersion()._2.contains("DIRTY")) {
        mill.api.Result.Failure("Project (git) state is dirty. Release not recommended!", Some(false))
      } else {
	println(s"Version: ${aspectj.publishVersion()}")
        true
      }
    }

    /** Test and release to Maven Central. */
    def release(
                 sonatypeCreds: String,
                 release: Boolean = true
               ): Command[Unit] = T.command {
      if (checkRelease()) {
        testCached()
        PublishModule.publishAll(
          sonatypeCreds = sonatypeCreds,
          release = release,
          publishArtifacts = Tasks(Seq(
            api.publishArtifacts,
            worker.publishArtifacts,
            aspectj.publishArtifacts
          )),
          readTimeout = 600000
        )()
        ()
      }
    }

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
