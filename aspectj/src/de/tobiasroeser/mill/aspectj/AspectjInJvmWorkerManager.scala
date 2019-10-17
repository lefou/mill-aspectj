package de.tobiasroeser.mill.aspectj

import mill.PathRef
import mill.api.Ctx

class AspectjInJvmWorkerManager(ctx: Ctx.Log) {

  private[this] var workerCache: Map[Seq[PathRef], (AspectjApi, Int)] = Map.empty

  def get(toolsClasspath: Seq[PathRef]): AspectjApi = {
    val (worker, count) = workerCache.get(toolsClasspath) match {
      case Some((w, count)) =>
        ctx.log.debug(s"Reusing existing AspectjWorker for classpath: ${toolsClasspath}")
        w -> count
      case None =>
        ctx.log.debug(s"Creating AspectjWorker for classpath: ${toolsClasspath}")
        new AspectjInJvmWorker(toolsClasspath.map(_.path)) -> 0
    }
    workerCache += toolsClasspath -> (worker -> (1 + count))
    worker
  }
}
