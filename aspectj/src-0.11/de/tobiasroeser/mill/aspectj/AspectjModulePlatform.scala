package de.tobiasroeser.mill.aspectj

import mill.{Agg, T}
import mill.api.PathRef
import mill.scalalib.{CoursierModule, Dep}

trait AspectjModulePlatform extends CoursierModule {

  /**
   * List of ivy dependencies to be used as aspect path (`ajc -aspectpath`).
   */
  def aspectIvyDeps: T[Agg[Dep]]

  /**
   * Resolved version of `aspectIvyDeps`.
   */
  def resolvedAspectIvyDeps: T[Agg[PathRef]] = T {
    resolveDeps(T.task { aspectIvyDeps().map(bindDependency()) }, false)()
  }

  def aspectjToolsDeps: T[Agg[Dep]]

  /**
   * The aspectj compiler classpath.
   * By default resolved from `aspectjToolsDeps`.
   */
  def aspectjToolsClasspath: T[Agg[PathRef]] = T {
    resolveDeps(T.task { aspectjToolsDeps().map(bindDependency()) })
  }

}
