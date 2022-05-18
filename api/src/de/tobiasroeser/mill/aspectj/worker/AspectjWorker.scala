package de.tobiasroeser.mill.aspectj.worker

import mill.api.{Ctx, Result}
import mill.scalalib.api.CompilationResult
import os.Path

trait AspectjWorker {

  /**
   * Invokes the Aspectj compiler
   * @param classpath
   * @param sourceFiles
   * @param options
   * @param aspectPath
   * @param inPath
   * @param allowConcurrentRuns If `false`, parallel executions of the compiler will be synchronized, to work around known concurrency issues.
   * @param ctx The mill execution context
   * @return
   */
  def compile(
    classpath: Seq[Path],
    sourceFiles: Seq[Path],
    options: Seq[String],
    aspectPath: Seq[Path],
    inPath: Seq[Path],
    allowConcurrentRuns: Boolean
  )(implicit ctx: Ctx): Result[CompilationResult]
}
