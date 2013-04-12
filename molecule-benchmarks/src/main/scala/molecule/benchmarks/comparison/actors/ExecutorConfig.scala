package molecule.benchmarks.comparison.actors

object ExecutorConfig {

  import mbench.benchmark._
  import molecule.platform.Executor

  def apply(executorName: String, executor: Int => Executor): RuntimeConfig[Int, Executor] =
    Config.runtime[Int, Executor](
      executorName,
      executor,
      _.shutdownNow()
    )

  val forkJoin: RuntimeConfig[Int, Executor] = apply("fj", Executors.forkJoin)

}