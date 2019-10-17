package de.tobiasroeser.mill.aspectj

import mill.T
import mill.define.{Discover, ExternalModule, Module, Worker}


trait AspectjWorkerModule extends Module {

  def aspectjWorkerManager: Worker[AspectjInJvmWorkerManager] = T.worker {
    new AspectjInJvmWorkerManager(T.ctx())
  }

}

object AspectjWorkerModule extends ExternalModule with AspectjWorkerModule {
  lazy val millDiscover = Discover[this.type]
}
