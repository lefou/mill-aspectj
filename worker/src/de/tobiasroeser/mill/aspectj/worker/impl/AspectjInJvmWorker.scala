package de.tobiasroeser.mill.aspectj.worker.impl

import java.io.File

import de.tobiasroeser.mill.aspectj.worker.AspectjWorker
import mill.api.{Ctx, PathRef, Result}
import mill.scalalib.api.CompilationResult
import org.aspectj.bridge.IMessage
import org.aspectj.tools.ajc.Main
import os.Path

class AspectjInJvmWorker() extends AspectjWorker {

  override def compile(
                        classpath: Seq[Path],
                        sourceDirs: Seq[Path],
                        options: Seq[String],
                        aspectPath: Seq[Path],
                        inPath: Seq[Path]
                      )(implicit ctx: Ctx): Result[CompilationResult] = {
    // synchronized: AspectJ compiler seems to fail unpredictably with ThreadDeath when run concurrently
    synchronized {
      internalCompile(classpath, sourceDirs, options, aspectPath, inPath)
    }
  }

    def internalCompile(
                        classpath: Seq[Path],
                        sourceDirs: Seq[Path],
                        options: Seq[String],
                        aspectPath: Seq[Path],
                        inPath: Seq[Path]
                      )(implicit ctx: Ctx): Result[CompilationResult] = {
    val dest = ctx.dest
    ctx.log.debug(s"Destination: ${dest}")

    val classesDir = dest / "classes"
    os.makeDir.all(classesDir)

    val (javaCount, ajCount) = sourceDirs.filter(os.isDir).foldLeft(0 -> 0) { (count, d) =>
      val files = os.walk(d).filter(os.isFile)
      val java = files.count(_.ext.equalsIgnoreCase("java"))
      val aspect = files.count(_.ext.equalsIgnoreCase("aj"))
      (count._1 + java, count._2 + aspect)
    }
    println(s"Compiling ${javaCount} Java sources and ${ajCount} AspectJ sources to ${dest.toIO.getPath()} ...")

    def asOptionalPath(name: String, paths: Seq[Path], filterExisting: Boolean = true): Seq[String] = {
      ctx.log.debug(s"unfiltered ${name}: ${paths.map(_.toIO.getPath()).mkString("\n  ", "\n  ", "")}")
      val ps = if (filterExisting) paths.filter(os.exists) else paths
      if (ps.isEmpty) Seq()
      else Seq(name, ps.map(_.toIO.getPath()).mkString(File.pathSeparator))
    }

    val ajcArgs = Seq(
      // explicit options,
      options,
      // classpath
      asOptionalPath("-cp", classpath),
      // aspectPath
      asOptionalPath("-aspectpath", aspectPath),
      // inPath
      asOptionalPath("-inpath", inPath),
      // sourceDirs
      asOptionalPath("-sourceroots", sourceDirs),
      // destination dir
      Seq("-d", classesDir.toIO.getPath()),
    ).flatten

    ctx.log.debug(s"ajc args: ${ajcArgs.mkString(" ")}")
    val prevSecManager = System.getSecurityManager()

    //    val messageHolder = new MessageHandler()

    val ajcMain = new Main()
    //    ajcMain.setHolder(messageHolder)
    ajcMain.runMain(ajcArgs.toArray[String], false)

    val analysisFile = dest / "analysis.dummy"
    os.write(target = analysisFile, data = "", createFolders = true)

    val res = CompilationResult(analysisFile, PathRef(classesDir))

    ajcMain.getMessageHandler().numMessages(IMessage.ERROR, true) match {
      case 0 =>  Result.Success(res)
      case n => Result.Failure(s"AspectJ compiler failed with ${n} errors", Some(res))
    }

  }

}
