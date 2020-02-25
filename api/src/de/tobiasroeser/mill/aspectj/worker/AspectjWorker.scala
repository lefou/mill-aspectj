package de.tobiasroeser.mill.aspectj.worker

import mill.api.{Ctx, Result}
import mill.scalalib.api.CompilationResult
import os.Path

trait AspectjWorker {
  def compile(
    classpath: Seq[Path],
    sourceDirs: Seq[Path],
    options: Seq[String],
    aspectPath: Seq[Path],
    inPath: Seq[Path]
  )(implicit ctx: Ctx): Result[CompilationResult]
}
