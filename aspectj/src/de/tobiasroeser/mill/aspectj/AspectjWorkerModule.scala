package de.tobiasroeser.mill.aspectj

import de.tobiasroeser.mill.aspectj.worker.AspectjWorkerManager
import mill.T
import mill.define.{Discover, ExternalModule, Module, Worker}


trait AspectjWorkerModule extends Module {

  def aspectjWorkerManager: Worker[AspectjWorkerManager] = T.worker {
    new AspectjInJvmWorkerManager(T.ctx())
  }

}

object AspectjWorkerModule extends ExternalModule with AspectjWorkerModule {
  lazy val millDiscover = Discover[this.type]
}
