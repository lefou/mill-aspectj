package de.tobiasroeser.mill.aspectj

import mill.{Agg, T}
import mill.api.PathRef
import mill.scalalib.{CoursierModule,Dep}

trait AspectjModulePlatform extends CoursierModule {

  def aspectIvyDeps: T[Agg[Dep]]

  /**
   * Resolved version of `aspectIvyDeps`.
   */
  def resolvedAspectIvyDeps: T[Agg[PathRef]] = T {
    resolveDeps(aspectIvyDeps, false)()
  }

  def aspectjToolsDeps: T[Agg[Dep]]

  /**
   * The aspectj compiler classpath.
   * By default resolved from `aspectjToolsDeps`.
   */
  def aspectjToolsClasspath: T[Agg[PathRef]] = T {
    resolveDeps(aspectjToolsDeps)
  }

}
