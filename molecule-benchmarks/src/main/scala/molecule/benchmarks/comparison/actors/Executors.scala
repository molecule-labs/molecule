package molecule.benchmarks.comparison.actors

object Executors {

  // This one has shutdown methods!
  import molecule.platform

  class FJExecutor(nbThreads: Int) extends platform.Executor {
    val s = new molecule.jsr166y.ForkJoinPool(nbThreads)

    def execute(r: Runnable) = try {
      s.execute(r)
    } catch {
      case e: java.util.concurrent.RejectedExecutionException => ()
    }

    def shutdown() = {
      s.shutdown()
    }

    def shutdownNow() = {
      s.shutdown()
    }
  }

  def forkJoin(nbThreads: Int): FJExecutor =
    new FJExecutor(nbThreads)

}