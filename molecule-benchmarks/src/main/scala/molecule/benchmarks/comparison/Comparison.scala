package molecule.benchmarks
package comparison

object Comparison {

  import mbench.properties._

  def cores = mbench.Host.Hardware.cores

  val properties = Properties.load("comparison.properties")

  // Framework names

  val scalaActors = "scala-actors"

  val molecule = "molecule"

  /**
   * The frameworks to include in benchmark.
   * (default: all)
   */
  val frameworks = Property("frameworks", properties, Many(
    molecule,
    scalaActors
  ))

  private def getBenchmarks: Seq[Benchmarks] = frameworks.get.collect {
    _ match {
      case `molecule` => comparison.molecule.Molecule
      case `scalaActors` => comparison.actors.Actors
    }
  }

  // Application names

  val chameneosRedux = "chameneos-redux"
  val primeSieve = "prime-sieve"
  val threadRing = "thread-ring"
  val pingPong = "ping-pong"

  /**
   * The applications to include in benchmark.
   * (default: all)
   */
  val apps = Property("apps", properties, Many(
    chameneosRedux,
    primeSieve,
    threadRing,
    pingPong
  ))

  /**
   * The applications to exclude from the benchmark.
   * (default: none)
   */
  val appsExclude = Property("tests.exclude", properties, Many.empty[String])

  val allApps = apps.get.filterNot(appsExclude.get.contains)

  //
  // Configuration types
  //

  /**
   * Execute the benchmark using a less exhaustive configuration
   * that takes less time to benchmark.
   * (default: true)
   */
  val quick = Property("quick", properties, true)

  //
  // Misc.
  //

  val warmups = Property("warmups", properties, 2)

  val runs = Property("warmups", properties, 5)

  /**
   * Minimum reference duration in seconds for tests that measure the throughput
   */
  val minDuration = Property("min.duration", properties, 1.0)

  import common._

  def main(args: Array[String]): Unit = {

    val benchmarks = getBenchmarks

    allApps foreach {
      case `chameneosRedux` =>
        val benchmark = CChameneosRedux.benchmark
        val config = CChameneosRedux.config
        val datFiles = benchmarks.flatMap(_.chameneosRedux(benchmark, config))
        CChameneosRedux.compare(datFiles)
      case `threadRing` =>
        val benchmark = CThreadRing.benchmark
        val config = CThreadRing.config
        val datFiles = benchmarks.flatMap(_.threadRing(benchmark, config))
        CThreadRing.compare(datFiles)
      case `pingPong` =>
        val benchmark = CPingPong.benchmark
        val config = CPingPong.config
        val datFiles = benchmarks.flatMap(_.pingPong(benchmark, config))
        CPingPong.compare(datFiles)
      case `primeSieve` =>
        val benchmark = CPrimeSieve.benchmark
        val config = CPrimeSieve.config
        val datFiles = benchmarks.flatMap(_.primeSieve(benchmark, config))
        CPrimeSieve.compare(datFiles)
      case unknown =>
        System.err.println("Test " + unknown + " unknown, skipping...")
    }
  }

}
