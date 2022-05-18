package de.tobiasroeser.mill.aspectj

/**
 * Decide want kind of sources the compiler is processing.
 */
sealed trait CompileMode

object CompileMode {

  /**
   * Process all source files (`*.java`  and `*.aj`)
   */
  final case object FullSources extends CompileMode

  /**
   * Only process `*.aj` files but weave-compile all other classes given via `-inpath`.
   * This setup requires another compiler to compile the Java (and other) source files to class files.
   */
  final case object OnlyAjSources extends CompileMode
}
