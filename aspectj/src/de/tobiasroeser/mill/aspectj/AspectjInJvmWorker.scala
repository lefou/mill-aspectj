package de.tobiasroeser.mill.aspectj

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.security.Permission
import java.{lang => jl}

import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

import mill.PathRef
import mill.api.Ctx
import mill.scalalib.api.CompilationResult
import os.Path

class AspectjInJvmWorker(toolsClasspath: Seq[Path]) extends AspectjApi {

  class CachedTool()(implicit ctx: Ctx) {
    ctx.log.debug(s"Creating Classloader with classpath: [${toolsClasspath}]")
    val classLoader = new URLClassLoader(toolsClasspath.map(_.toNIO.toUri().toURL()).toArray[URL], null)

    val mainClass = classLoader.loadClass("org.aspectj.tools.ajc.Main")
    val runMainMethod = mainClass.getMethod("runMain", Seq[Class[_]](classOf[Array[String]], classOf[Boolean]): _*)
  }

  var cache0: Option[CachedTool] = None

  def cache(implicit ctx: Ctx): CachedTool = cache0 match {
    case Some(c) => c
    case None =>
      val cache = new CachedTool()
      cache0 = Some(cache)
      cache
  }

  override def compile(
    classpath: Seq[Path],
    sourceDirs: Seq[Path],
    options: Seq[String],
    aspectPath: Seq[Path],
    inPath: Seq[Path]
  )(implicit ctx: Ctx): CompilationResult = synchronized {
    val dest = ctx.dest
    ctx.log.debug(s"Destination: ${dest}")
    val cache = this.cache

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

    // We need a security manager to intercept the System.exit calls
    // TODO: later we will use a message handler to intercept compiler errors

    val useSystemExit: jl.Boolean = true

    val securityManager = new SecurityManager() {
      override def checkPermission(perm: Permission): Unit = {
        Option(prevSecManager).foreach(_.checkPermission(perm))
      }

      override def checkPermission(perm: Permission, context: Any): Unit = {
        Option(prevSecManager).foreach(_.checkPermission(perm, context))
      }

      override def checkExit(status: Int): Unit = {
        throw new SecurityException("ajc exit " + status)
      }
    }

    System.setSecurityManager(securityManager)
    try {
      val ajcMain = cache.mainClass.newInstance()
      cache.runMainMethod.invoke(ajcMain, Seq[AnyRef](ajcArgs.toArray[String], useSystemExit): _*)
    } catch {
      case e: InvocationTargetException if e.getCause().isInstanceOf[SecurityException] && e.getCause().getMessage() == "ajc exit 0" =>
      // ajc exited successfully
      case e: InvocationTargetException if e.getCause().isInstanceOf[SecurityException] =>
        throw new RuntimeException(e.getCause().getMessage())
    } finally {
      if (!securityManager.equals(System.getSecurityManager())) {
        ctx.log.error("Internal inconsistency detected: SecurityManager changed unexpectedly.")
      }
      System.setSecurityManager(prevSecManager)
    }

    val analysisFile = dest / "analysis.dummy"
    os.write(target = analysisFile, data = "", createFolders = true)

    val res = CompilationResult(analysisFile, PathRef(classesDir))
    ctx.log.debug(s"ajc compile result: ${res}")
    res
  }

}
