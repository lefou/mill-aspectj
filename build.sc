import mill._
import mill.define.{Module, TaskModule}
import mill.scalalib._
import mill.scalalib.publish._

import $ivy.`de.tototec::de.tobiasroeser.mill.integrationtest:0.1.0`, de.tobiasroeser.mill.integrationtest._


object Deps {
  def millVersion = "0.5.0"
  def scalaVersion = "2.12.8"

  val logbackClassic = ivy"ch.qos.logback:logback-classic:1.1.3"
  val millMain = ivy"com.lihaoyi::mill-main:${millVersion}"
  val millScalalib = ivy"com.lihaoyi::mill-scalalib:${millVersion}"
  val scalaTest = ivy"org.scalatest::scalatest:3.0.1"
  val slf4j = ivy"org.slf4j:slf4j-api:1.7.25"
}

trait MillAjcModule extends ScalaModule with PublishModule {

  def scalaVersion = T { Deps.scalaVersion }

  override def ivyDeps = T {
    Agg(ivy"${scalaOrganization()}:scala-library:${scalaVersion()}")
  }

  def publishVersion = GitSupport.publishVersion()._2

  override def javacOptions = Seq("-source", "1.8", "-target", "1.8")

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

object aspectj extends MillAjcModule {

  override def artifactName = T { "de.tobiasroeser.mill.aspectj" }

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

}

object it extends MillIntegrationTestModule {

  def millTestVersion = T {
    val ctx = T.ctx()
    ctx.env.get("TEST_MILL_VERSION").getOrElse(Deps.millVersion)
  }

  def pluginsUnderTest = Seq(aspectj)

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

/** Build JARs. */
def Tbuild() = T.command {
  aspectj.jar()
}

/** Run tests. */
def test() = T.command {
  aspectj.test.test()()
  it.test()()
}

def install() = T.command {
  T.ctx().log.info("Installing")
  test()()
  aspectj.publishLocal()()
}

def checkRelease: T[Boolean] = T.input {
  if (GitSupport.publishVersion()._2.contains("DIRTY")) {
    T.ctx().log.error("Project (git) state is dirty. Release not recommended!")
    false
  } else { true }
}

/** Test and release to Maven Central. */
def release(
             sonatypeCreds: String,
             release: Boolean = true
           ) = T.command {
  if (checkRelease()) {
    test()()
    aspectj.publish(sonatypeCreds = sonatypeCreds, release = release)()
  }
}
