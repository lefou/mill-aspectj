package de.tobiasroeser.mill.aspectj

import java.net.URL

import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

import mill._
import mill.api.Ctx.{Dest, Log}
import mill.define.{Target, Task, Worker}
import mill.scalalib.{Dep, DepSyntax, JavaModule}
import mill.scalalib.api.CompilationResult
import os.Path
import java.{lang => jl}

import mill.api.Loose

trait AspectjModule extends JavaModule {

  /**
   * The AspectJ version.
   */
  def aspectjVersion: T[String]

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
  def aspectjToolsDeps: T[Agg[Dep]] = T {
    Agg(ivy"org.aspectj:aspectjtools:${aspectjVersion()}")
  }

  /**
   * The aspectj compiler classpath.
   * By default resolved from `aspectjToolsDeps`.
   */
  def aspectjToolsClasspath: T[Agg[PathRef]] = T {
    resolveDeps(aspectjToolsDeps)
  }

  def aspectjWorker: Worker[AspectjWorker] = T.worker {
    new AspectjWorkerImpl(aspectjToolsClasspath().toSeq.map(_.path))
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
  def aspectIvyDeps: T[Agg[Dep]] = T {
    Agg.empty[Dep]
  }

  /**
   * Effective aspect path (`ajc -inpath`).
   * In most cases, it is enough to use `aspectModuleDeps` and `aspectIvyDeps`.
   */
  def effectiveAspectPath: T[Seq[PathRef]] = T {
    Task.traverse(aspectModuleDeps)(_.localClasspath)().flatten ++
      resolveDeps(aspectIvyDeps, false)() ++
      aspectPath()
  }

  /**
   * List of directories with `.class` files to weave (into target directory).
   * Corresponds to `ajc -inpath` option.
   */
  def weavePath: T[Seq[PathRef]] = T {
    Seq[PathRef]()
  }

  /**
   * Compiles the source code with the ajc compiler.
   */
  override def compile: T[CompilationResult] = T {
    aspectjWorker().compile(
      classpath = compileClasspath().toSeq.map(_.path),
      sourceDirs = allSources().map(_.path),
      options = ajcOptions(),
      aspectPath = effectiveAspectPath().toSeq.map(_.path),
      inPath = weavePath().map(_.path)
    )(T.ctx())
  }

  /**
   * Shows the help of the AspectJ compiler (`ajc -help`).
   */
  def ajcHelp() = T.command {
    aspectjWorker().compile(
      classpath = aspectjToolsClasspath().toSeq.map(_.path),
      sourceDirs = Seq(),
      options = Seq("-help"),
      aspectPath = Seq(),
      inPath = Seq()
    )
    println()
    ()
  }

}

