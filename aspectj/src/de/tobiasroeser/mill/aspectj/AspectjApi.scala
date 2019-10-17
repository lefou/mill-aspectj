package de.tobiasroeser.mill.aspectj

import mill.api.Ctx
import mill.scalalib.api.CompilationResult
import os.Path

trait AspectjApi {
  def compile(
    classpath: Seq[Path],
    sourceDirs: Seq[Path],
    options: Seq[String],
    aspectPath: Seq[Path],
    inPath: Seq[Path]
  )(implicit ctx: Ctx): CompilationResult
}
