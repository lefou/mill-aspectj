package de.tobiasroeser.mill.aspectj

import java.net.{URL, URLClassLoader}

import de.tobiasroeser.mill.aspectj.worker.{AspectjWorker, AspectjWorkerManager}
import mill.PathRef
import mill.api.Ctx

class AspectjInJvmWorkerManager(ctx: Ctx.Log) extends AspectjWorkerManager {

  private[this] var workerCache: Map[Seq[PathRef], (AspectjWorker, Int)] = Map.empty

  def get(toolsClasspath: Seq[PathRef])(implicit ctx: Ctx): AspectjWorker = {
    val (worker, count) = workerCache.get(toolsClasspath) match {
      case Some((w, count)) =>
        ctx.log.debug(s"Reusing existing AspectjWorker for classpath: ${toolsClasspath}")
        w -> count
      case None =>
        ctx.log.debug(s"Creating Classloader with classpath: [${toolsClasspath}]")
        val classLoader = new URLClassLoader(
          toolsClasspath.map(_.path.toNIO.toUri().toURL()).toArray[URL],
          getClass().getClassLoader()
        )

        ctx.log.debug(s"Creating AspectjWorker for classpath: ${toolsClasspath}")
        val className = classOf[AspectjWorker].getPackage().getName() + ".impl.AspectjInJvmWorker"
        val impl = classLoader.loadClass(className)
        val worker = impl.newInstance().asInstanceOf[AspectjWorker]
        if (worker.getClass().getClassLoader() != classLoader) {
          ctx.log.error(
            """Worker not loaded from worker classloader.
              |You should not add the mill-aspectj-worker JAR to the mill build classpath""".stripMargin
          )
        }
        if (worker.getClass().getClassLoader() == classOf[AspectjWorker].getClassLoader()) {
          ctx.log.error("Worker classloader used to load interface and implementation")
        }
        worker -> 0
    }
    workerCache += toolsClasspath -> (worker -> (1 + count))
    worker
  }
}
