package molecule
package benchmarks.comparison
package molecule

object Molecule extends Benchmarks {

  import mbench.benchmark._
  import mbench.properties._
  import mbench.gnuplot._

  val properties = Properties.load("molecule.properties")

  //
  // Schedulers
  //

  val actorLike = "actor-like"
  val fptp = "fptp" // obsolete: FJ always win
  val fpfj = "fpfj"
  val wctp = "wctp"
  val wcfj = "wcfj"

  val schedulers = Property("schedulers", properties, Many(
    actorLike,
    fpfj,
    wcfj
  ))

  val cacheEffects = Property("cache.effects", properties, false)

  import molecule.SchedulerConfig
  import platform.Platform
  import Comparison.{ cores, minDuration }
  import common._

  def threadRing(
    benchmark: Benchmark[Int, CThreadRing.Config, DatFile],
    config: StaticConfig[CThreadRing.Config]): Seq[DatFile] = {

    if (cacheEffects.get) threadRingCacheEffects(config.value)

    val testWord = Test.runtimeStatic("molecule-word", ThreadRing.runWord)
    val testCore = Test.runtimeStatic("molecule-core", ThreadRing.runCore)
    val testStream = Test.runtimeStatic("molecule-stream", ThreadRing.runStream)

    val scheds = schedulers.get.intersect(Seq(actorLike, fpfj, wcfj))

    val myBenchmark = benchmark.copy(runs = 9)

    val datFiles =
      scheds.flatMap(schedulerName => {

        val scheduler = SchedulerConfig(schedulerName)

        // perform a dry run with SST = 0 to estimate the execution time and shorten it to 'minDuration' 
        val dryConfig = SchedulerConfig(schedulerName) and config
        val m = myBenchmark.dryRun(2, 1, dryConfig, testWord)
        val newConfig = config.update(common =>
          common.copy(N = (minDuration.get * (common.N / m.time)).toInt)
        )

        val fullConfig = scheduler and newConfig
        val datFiles = Seq(testCore, testStream, testWord) map (myBenchmark(fullConfig, _))

        CThreadRing.plot(datFiles)
        datFiles
      })

    Seq(datFiles.last)
  }

  def threadRingCacheEffects(config: CThreadRing.Config): Unit = {

    val xlabel = Label[Int]("nodes")

    val throughput = Column.withConfig[Int, Int, Double]("throughput", "msg".perSeconds)(
      (size, n, time) => n / time
    )

    val benchmark =
      Benchmark(Comparison.threadRing + "-cache-effects", 2 to 100, xlabel, warmups = 2, runs = 7)
        .add(throughput)

    def mkTest(name: String, run: (Platform, CThreadRing.Config) => Unit) =
      Test[Platform, Int, Int]("name",
        (platform, N, size) => run(platform, CThreadRing.Config(N, size)))

    val testCore = mkTest("molecule-core", ThreadRing.runCore)
    val testStream = mkTest("molecule-stream", ThreadRing.runStream)
    val testWord = mkTest("molecule-word", ThreadRing.runWord)

    val scheduler = SchedulerConfig(fptp)

    // perform a dry run with a size of 100 to estimate the execution time and shorten it to 'minDuration' 
    val dryConfig = SchedulerConfig(fptp) and Config.static(config.N)
    val m = benchmark.dryRun(2, 100, dryConfig, testWord)
    val N = (minDuration.get * (config.N / m.time)).toInt

    val fullConfig = scheduler and Config.static(N)

    val datFiles = Seq(testCore, testStream, testWord) map (benchmark(fullConfig, _))

    CThreadRing.plot(datFiles)

  }

  def pingPong(
    benchmark: Benchmark[Int, Int, DatFile],
    config: StaticConfig[Int]): Seq[DatFile] = {

    val test = Test("molecule-io", PingPong.run)

    val scheduler = SchedulerConfig(wcfj)

    // perform a dry run to estimate the execution time and shorten it to 'minDuration' 
    val dryConfig = scheduler and config
    val m = benchmark.dryRun(2, 1, dryConfig, test)
    val newConfig = config.update(cycles => (minDuration.get * (cycles / m.time)).toInt)

    val fullConfig = scheduler and newConfig
    Seq(benchmark(fullConfig, test))
  }

  def primeSieve(
    benchmark: Benchmark[Int, CPrimeSieve.Config, DatFile],
    config: StaticConfig[CPrimeSieve.Config]): Seq[DatFile] = {

    val thresholds = {
      if (Comparison.quick.get)
        Seq((10, 10), (40, 40), (50, 50))
      else
        Seq((1, 1), (10, 10), (10, 40), (40, 10), (40, 40), (50, 50))
    }

    val ebenchmark = benchmark.extend[(Int, Int)]

    val testCore = Test.runtimeStatic("molecule-core", PrimeSieve.runCore)
    val testIO = Test.runtimeStatic("molecule-io", PrimeSieve.runIO)

    schedulers.get.flatMap(schedulerName => {

      val scheduler = SchedulerConfig(schedulerName)
      val configs = thresholds.map({
        case (sst, cct) => scheduler and config.extend(Config.static("SST=" + sst + "-CCT=" + cct, (sst, cct)))
      })

      val datFile1 = ebenchmark(configs.last, testCore)

      val datFiles = configs.map(ebenchmark(_, testIO))

      CPrimeSieve.plot(datFiles)
      Seq(datFiles.head, datFiles.last, datFile1) // slowest and fastest ones correspond to min and max thresholds
    })
  }

  def chameneosRedux(
    benchmark: Benchmark[Int, CChameneosRedux.Config, DatFile],
    config: StaticConfig[CChameneosRedux.Config]): Seq[DatFile] = {

    val segments =
      if (Comparison.quick.get)
        List(0, 6, cores / 2, cores).distinct
      else
        List(0, 1, 2, 6, cores / 2, cores, cores * 2).distinct

    val testIO = Test.runtimeStatic("molecule-io", ChameneosRedux.runIO)

    // Extend the static benchmark configuration with the SST
    val ebenchmark = benchmark.extend[Int]

    val ioDats = schedulers.get.map(schedulerName => {
      _chameneosRedux(segments, ebenchmark, SchedulerConfig(schedulerName), config, testIO)
    })

    val testCore = Test.runtimeStatic("molecule-core", ChameneosRedux.runCore)

    val coreDat =
      _chameneosRedux(segments, ebenchmark, SchedulerConfig.wcfj, config, testCore)

    coreDat +: ioDats
  }

  private def _chameneosRedux(
    segments: List[Int],
    benchmark: Benchmark[Int, (CChameneosRedux.Config, Int), DatFile],
    runtimeConfig: RuntimeConfig[Int, Platform],
    staticConfig: StaticConfig[CChameneosRedux.Config],
    test: RuntimeStaticTest[Platform, (CChameneosRedux.Config, Int)]): DatFile = {

    // perform a dry run with SST = 0 to estimate the execution time and shorten it to 'minDuration' 
    val dryConfig = runtimeConfig and staticConfig.extend(Config.static(0))
    val m = benchmark.dryRun(2, 1, dryConfig, test)
    val newConfig = staticConfig.update(common =>
      common.copy(nbMeetings = (minDuration.get * (common.nbMeetings / m.time)).toInt))

    val configs =
      segments.map(sst => runtimeConfig and newConfig.extend(Config.static("SST=" + sst, sst)))

    val datFiles = configs.map(benchmark(_, test))

    CChameneosRedux.plot(datFiles)
    datFiles.head // Keep only results for which SST = 0

  }

}