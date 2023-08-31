package de.tobiasroeser.mill.aspectj

import java.net.URL

import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

import mill._
import mill.api.Ctx.{Dest, Log}
import mill.define.{Command, Target, Task, Worker}
import mill.scalalib.{Dep, DepSyntax, GenIdeaImpl, JavaModule}
import mill.scalalib.api.CompilationResult
import os.Path
import java.{lang => jl}

import de.tobiasroeser.mill.aspectj.worker.AspectjWorker
import mill.scalalib.GenIdeaModule.{Element, IdeaConfigFile, JavaFacet}
import mill.api.{Ctx, Loose}

trait AspectjModule extends JavaModule with AspectjModulePlatform {
  def aspectjWorkerModule: AspectjWorkerModule = AspectjWorkerModule

  /**
   * The AspectJ version.
   */
  def aspectjVersion: T[String]

  def aspectjCompileMode: CompileMode = CompileMode.FullSources

  /**
   * The compile and runtime dependencies.
   * Contains by default the `aspectrt.jar` which is resolved via ivy (`ivy"org.aspectj:aspectrt:$${aspectjVersion()}"`).
   * If you do do not use `super.ivyDeps()` when overriding this def, you need to provide the `aspectrt.jar` manually.
   */
  override def ivyDeps: T[Agg[Dep]] = T {
    super.ivyDeps() ++ Agg(ivy"org.aspectj:aspectjrt:${aspectjVersion()}")
  }

  /**
   * The ivy dependencies representing the aspectj compiler classes, which is typically a `aspectjtools.jar`.
   * Default to `ivy"org.aspectj:aspectjtools:$${aspectjVersion()}"`.
   */
  override def aspectjToolsDeps: T[Agg[Dep]] = T {
    Agg(
      ivy"org.aspectj:aspectjtools:${aspectjVersion()}",
      ivy"${Versions.millAspectjWorkerImplIvyDep}"
    )
  }

  /**
   * This is now deprecated, as it triggers the worker initialization even when the worker is not needed.
   * Once initialized, the actual worker will be cached by `aspectjWorkerModule`,
   * but only when the worker is really needed.
   */
  @deprecated("Use aspectjWorkerTask instead.", "mill-aspectj after 0.3.1")
  def aspectjWorker: Worker[AspectjWorker] = T.worker {
    aspectjWorkerTask()
  }

  protected def aspectjWorkerTask: Task[AspectjWorker] = T.task {
    aspectjWorkerModule.aspectjWorkerManager().get(aspectjToolsClasspath().toSeq)
  }

  /**
   * Additional options to be used by `ajc` in the `compile` target.
   */
  def ajcOptions: T[Seq[String]] = T {
    Seq[String]()
  }

  /**
   * Additional classes, JARs or ZIPs to be used as aspect path (`ajc -aspectpath`).
   * In most cases it is enough to use `aspectModuleDeps` and `aspectIvyDeps`.
   */
  def aspectPath: T[Agg[PathRef]] = T {
    Agg[PathRef]()
  }

  /**
   * List of modules to be used as aspect path (`ajc -aspectpath`).
   */
  def aspectModuleDeps: Seq[JavaModule] = Seq()

  /**
   * List of ivy dependencies to be used as aspect path (`ajc -aspectpath`).
   */
  override def aspectIvyDeps: T[Agg[Dep]] = T {
    Agg.empty[Dep]
  }

  /**
   * Effective aspect path (`ajc -aspectpath`).
   * In most cases, it is enough to use `aspectModuleDeps` and `resolvedAspectIvyDeps`.
   */
  def effectiveAspectPath: T[Seq[PathRef]] = T {
    Target.traverse(aspectModuleDeps)(_.localClasspath)().flatten ++
      resolvedAspectIvyDeps() ++
      aspectPath()
  }

  /**
   * List of directories with `.class` files to weave (into target directory).
   * Corresponds to `ajc -inpath` option.
   */
  def weavePath: T[Seq[PathRef]] = T {
    Seq[PathRef]()
  }

  def aspectjAllowConcurrentRuns = false

  /**
   * Compiles the source code with the ajc compiler.
   */
  override def compile: T[CompilationResult] = T {
    ajcTask()()
  }

  def ajcSourceFiles: T[Seq[PathRef]] = T {
    val exts = aspectjCompileMode match {
      case CompileMode.OnlyAjSources => Seq("aj")
      case CompileMode.FullSources => Seq("java", "aj")
    }
    findSourceFiles(allSources(), exts).map(PathRef(_))
  }

  private def finalInPath: T[Seq[PathRef]] = aspectjCompileMode match {
    case CompileMode.OnlyAjSources => T {
        weavePath() ++ Seq(super.compile().classes)
      }
    case CompileMode.FullSources => T {
        weavePath()
      }
  }

  // copy of mill.scalalib.Lib.findSourceFiles, as it is not present in Mill 0.9 and older
  private def findSourceFiles(sources: Seq[PathRef], extensions: Seq[String]): Seq[os.Path] = {
    def isHiddenFile(path: os.Path) = path.last.startsWith(".")
    for {
      root <- sources
      if os.exists(root.path)
      path <- (if (os.isDir(root.path)) os.walk(root.path) else Seq(root.path))
      if os.isFile(path) && (extensions.exists(path.ext == _) && !isHiddenFile(path))
    } yield path
  }

  def ajcTask(extraArgs: String*): Task[CompilationResult] = T.task {
    aspectjWorkerTask().compile(
      classpath = compileClasspath().toSeq.map(_.path),
      sourceFiles = ajcSourceFiles().map(_.path),
      options = extraArgs ++ ajcOptions(),
      aspectPath = effectiveAspectPath().toSeq.map(_.path),
      inPath = finalInPath().map(_.path),
      allowConcurrentRuns = aspectjAllowConcurrentRuns
    )
  }

  /**
   * Shows the help of the AspectJ compiler (`ajc -help`).
   */
  def ajcHelp(extraArgs: String*): Command[Unit] = T.command {
    ajcTask("-help")()
    // terminate with a new line
    println()
  }

}
