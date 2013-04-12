package molecule.benchmarks.comparison
package common

object CChameneosRedux {

  case class Config(
      nbMeetings: Int,
      nbChameneos: Int) {
    override def toString =
      "nbMeetings=" + nbMeetings +
        ", nbChameneos=" + nbChameneos
  }

  import Comparison.{ cores, warmups, runs }
  import mbench.benchmark._
  import mbench.MBench.benchmarkFolder

  def config = mbench.benchmark.Config.static(Config(300000, 300))

  val xlabel = Label[Int]("threads")

  val throughput = Column.withConfig[Int, Config, Double]("throughput", "meetings".perSeconds)(
    (threads, config, time) => config.nbMeetings / time
  )

  val speedup = throughput.speedupHigherIsBetter

  def benchmark = if (Comparison.quick.get) quick else default

  private val default = {
    val threads =
      if (cores < 8) List(1) ++ (2 to (cores) by 2) ++ Seq(2, 4).map(_ + cores)
      else Seq(1, 2, 3, 4, 5) ++ (6 to cores by 2) ++ Seq(4, 8).map(_ + cores)

    Benchmark(Comparison.chameneosRedux, threads, xlabel, warmups.get, runs.get)
      .add(throughput)
      .add(speedup)
  }

  private lazy val quick =
    default.copy(
      is = if (cores < 8) default.is
      else Seq(2, 4, 5) ++ (6 to cores by 4) ++ Seq(4, 8).map(_ + cores)
    )

  import mbench.gnuplot._

  private def settings = Seq(Plot.xtics(1))
  private def labels = Seq(throughput.label, speedup.label)

  def plot(datfiles: Seq[DatFile]): Unit =
    Gnuplot.save(Gnuplot(datfiles, settings, labels))

  def compare(datfiles: Seq[DatFile]): Unit =
    Gnuplot.save(benchmarkFolder("overview"), Gnuplot(Comparison.chameneosRedux, datfiles, settings, labels))

}