package molecule.benchmarks.core

import mbench.benchmark._
import molecule.examples.core.ChameneosRedux.run
import molecule.platform.Platform

object ChameneosRedux {

  def main(args: Array[String]): Unit = {
    val nbChameneos = 300
    val nbMeetings = 300000
    val config = Config.runtime[Platform](Platform("chameneos-redux"), _.shutdown())

    val throughput = Column[Int, Double]("throughput", "meetings".perSeconds)(
      (nbMeetings, time) => nbMeetings / time
    )

    val benchmark =
      Benchmark("chameneos-redux", Seq(nbMeetings), Label("N"), TableReporter, warmups = 2, runs = 5)
        .add(throughput)

    val table = benchmark(config, Test.runtime[Platform, Int]("run", (platform, nbMeetings) => run(platform, platform, nbChameneos, nbMeetings, 0)))
    println(table)
  }

  // Intel Core 2 Duo CPU P8400 2.24 Ghz: -server
  // [N=300000] -> [time=.816s][cvar=2.800%][throughput=367647.059 meetings/s]

}