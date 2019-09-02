package de.tobiasroeser.mill.aspectj

import mill.api.Ctx
import mill.{PathRef, T}
import mill.define.{Discover, ExternalModule, Module, Worker}

object AspectjWorkerModule extends ExternalModule with AspectjWorkerModule {
  lazy val millDiscover = Discover[this.type]
}

trait AspectjWorkerModule extends Module {

  def aspectjWorkerManager: Worker[AspectjWorkerManager] = T.worker[AspectjWorkerManager] {
    new AspectjWorkerManager(T.ctx())
  }

}

