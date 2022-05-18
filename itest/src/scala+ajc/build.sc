import mill._
import mill.scalalib._
import mill.define._

import $exec.plugins
import de.tobiasroeser.mill.aspectj._

import $ivy.`org.scalatest::scalatest:3.2.10`
import org.scalatest.Assertions

object main extends ScalaModule with AspectjModule {

  def scalaVersion = "2.13.8"
  def aspectjVersion = "1.9.5"
  def aspectjCompileMode = CompileMode.OnlyAjSources

  override def scalacOptions = Seq("-target:jvm-1.8")
  override def ajcOptions = Seq("-1.8")

  object test extends Tests {
    override def javacOptions = Seq("-source", "1.8", "-target", "1.8")
    override def scalacOptions = Seq("-target:jvm-1.8")
    def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
    override def ivyDeps = Agg(
      ivy"com.novocode:junit-interface:0.11",
      ivy"de.tototec:de.tobiasroeser.lambdatest:0.7.0"
    )
  }

}

def verify(): Command[Unit] = T.command {

  val A = new Assertions{}
  import A._

  val cr = main.compile()
  main.test.test()()

  assert(main.ajcSourceFiles().map(_.path) == Seq(main.millSourcePath / "src" / "BeforeAspect.aj"))

  ()
}
