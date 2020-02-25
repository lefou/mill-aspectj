package de.tobiasroeser.mill.aspectj.worker

import mill.api.{Ctx, PathRef}

trait AspectjWorkerManager {
  def get(toolsClasspath: Seq[PathRef])(implicit ctx: Ctx): AspectjWorker
}
