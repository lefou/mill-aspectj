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

trait AspectjModule extends JavaModule {
  def aspectjWorkerModule: AspectjWorkerModule = AspectjWorkerModule

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
    Agg(
      ivy"org.aspectj:aspectjtools:${aspectjVersion()}",
      ivy"${Versions.millAspectjWorkerImplIvyDep}"
    )
  }

  /**
   * The aspectj compiler classpath.
   * By default resolved from `aspectjToolsDeps`.
   */
  def aspectjToolsClasspath: T[Agg[PathRef]] = T {
    resolveDeps(aspectjToolsDeps)
  }

  def aspectjWorker: Worker[AspectjWorker] = T.worker {
    // new AspectjWorkerImpl(aspectjToolsClasspath().toSeq.map(_.path))
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
  def aspectIvyDeps: T[Agg[Dep]] = T {
    Agg.empty[Dep]
  }

  /**
   * Resolved version of `aspectIvyDeps`.
   */
  def resolvedAspectIvyDeps: T[Agg[PathRef]] = T {
    resolveDeps(aspectIvyDeps, false)()
  }

  /**
   * Effective aspect path (`ajc -inpath`).
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

  def ajcTask(extraArgs: String*): Task[CompilationResult] = T.task {
    aspectjWorker().compile(
      classpath = compileClasspath().toSeq.map(_.path),
      sourceDirs = allSources().map(_.path),
      options = extraArgs ++ ajcOptions(),
      aspectPath = effectiveAspectPath().toSeq.map(_.path),
      inPath = weavePath().map(_.path),
      allowConcurrentRuns = aspectjAllowConcurrentRuns
    )
  }

  /**
   * Shows the help of the AspectJ compiler (`ajc -help`).
   */
  def ajcHelp(extraArgs: String*): Command[Unit] = T.command {
    ajcTask("-help")
    // terminate with a new line
    println()
  }

}

trait AspectjIdeaSupport extends AspectjModule {
  // Experimental support for Aspectj compiler config
  override def ideaConfigFiles(ideaConfigVersion: Int): Command[Seq[IdeaConfigFile]] = T.command {
    ideaConfigVersion match {
      case 4 =>
        aspectjToolsClasspath().toIndexedSeq match {
          case IndexedSeq() =>
            Seq()
          case toolsPath =>
            Seq(
              IdeaConfigFile(
                name = "compiler.xml",
                component = "AjcSettings",
                config = Seq(Element("option", Map("name" -> "ajcPath", "value" -> toolsPath.head.path.toIO.getPath())))
              ),
              IdeaConfigFile(
                name = "compiler.xml",
                component = "CompilerConfiguration",
                config = Seq(Element("option", Map("name" -> "DEFAULT_COMPILER", "value" -> "ajc")))
              )
            )
        }
      case v =>
        T.ctx().log.error(s"Unsupported Idea config version ${v}")
        Seq()

    }
  }

  // experimental support for AspectJ facets
  override def ideaJavaModuleFacets(ideaConfigVersion: Int): Command[Seq[JavaFacet]] = T.command {
    ideaConfigVersion match {
      case 4 =>
        val aspectPath =
          resolvedAspectIvyDeps().toSeq.map { depPathRef =>
            Element("projectLibrary", childs = Seq(
              Element("option", Map("name" -> "name", "value" -> depPathRef.path.last))
            ))
          } ++ aspectModuleDeps.map { module =>
            Element("module", childs = Seq(
              Element("option", Map("name" -> "name", "value" -> GenIdeaImpl.moduleName(module.millModuleSegments)))
            ))
          }

        Seq(
          JavaFacet("AspectJ", "AspectJ", config =
            Element("configuration", childs = if(aspectPath.isEmpty) Seq() else Seq(
              Element(
                "option",
                attributes = Map("name" -> "aspectPath"),
                childs = aspectPath
              )
            ))
          )
        )
      case v =>
        T.ctx().log.error(s"Unsupported Idea config version ${v}")
        Seq()
    }
  }
}