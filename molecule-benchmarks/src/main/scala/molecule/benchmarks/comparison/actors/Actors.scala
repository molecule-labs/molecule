package molecule
package benchmarks.comparison
package actors

object Actors extends Benchmarks {

  import mbench.benchmark._
  import mbench.properties._
  import mbench.gnuplot._
  import common._
  import platform.Executor

  import Comparison.minDuration

  def threadRing(
    benchmark: Benchmark[Int, CThreadRing.Config, DatFile],
    config: StaticConfig[CThreadRing.Config]): Seq[DatFile] = {

    val fullConfig = ExecutorConfig.forkJoin and config

    val test = Test.runtimeStatic[Executor, CThreadRing.Config]("scala-actors", (executor, config) =>
      ThreadRing.run(executor, config.size, config.N))

    // This test is only interested in the throughput
    // perform a dry run to estimate the execution time and shorten it to 'minDuration'
    val m = benchmark.dryRun(2, 1, fullConfig, test)
    val newConfig = config.update(common =>
      common.copy(N = (minDuration.get * (common.N / m.time)).toInt))

    val datFiles = Seq(benchmark(ExecutorConfig.forkJoin and newConfig, test))

    CThreadRing.plot(datFiles)
    datFiles
  }

  def pingPong(
    benchmark: Benchmark[Int, Int, DatFile],
    config: StaticConfig[Int]): Seq[DatFile] = {

    val test = Test("scala-actors", PingPong.run)

    val executor = ExecutorConfig.forkJoin

    // perform a dry run to estimate the execution time and shorten it to 'minDuration' 
    val dryConfig = executor and config
    val m = benchmark.dryRun(2, 1, dryConfig, test)
    val newConfig = config.update(cycles => (minDuration.get * (cycles / m.time)).toInt)

    val fullConfig = executor and newConfig
    Seq(benchmark(fullConfig, test))
  }

  def primeSieve(
    benchmark: Benchmark[Int, CPrimeSieve.Config, DatFile],
    config: StaticConfig[CPrimeSieve.Config]): Seq[DatFile] = {

    val test = Test.runtimeStatic[Executor, CPrimeSieve.Config]("scala-actors",
      (executor, config) => PrimeSieve.run(executor, config.N))

    val datFiles = Seq(benchmark(ExecutorConfig.forkJoin and config, test))
    CPrimeSieve.plot(datFiles)
    datFiles
  }

  def chameneosRedux(
    benchmark: Benchmark[Int, CChameneosRedux.Config, DatFile],
    config: StaticConfig[CChameneosRedux.Config]): Seq[DatFile] = {

    val executor = ExecutorConfig.forkJoin

    val test = Test.runtimeStatic[Executor, CChameneosRedux.Config]("scala-actors",
      (executor, config) => ChameneosRedux.run(executor, config.nbChameneos, config.nbMeetings))

    // This test is only interested in the throughput
    // perform a dry run to estimate the execution time and shorten it to 'minDuration' 
    val dryConfig = executor and config
    val m = benchmark.dryRun(2, 1, dryConfig, test)
    val newConfig = config.update(common =>
      common.copy(nbMeetings = (minDuration.get * (common.nbMeetings / m.time)).toInt)
    )

    val datFiles = Seq(benchmark(executor and newConfig, test))
    CChameneosRedux.plot(datFiles)
    datFiles
  }

}