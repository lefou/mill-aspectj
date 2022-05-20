import mill._
import mill.scalalib._
import mill.define._

import $exec.plugins
import de.tobiasroeser.mill.aspectj._

import $ivy.`org.scalatest::scalatest:3.2.10`
import org.scalatest.Assertions

object main extends AspectjModule {

  def aspectjVersion = "1.9.7"

  def ajcOptions = Seq("-1.8")

  object test extends Tests {
    def javacOptions = Seq("-source", "1.8", "-target", "1.8")
    def testFrameworks = Seq("com.novocode.junit.JUnitFramework")
    def ivyDeps = Agg(
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

  ()
}
