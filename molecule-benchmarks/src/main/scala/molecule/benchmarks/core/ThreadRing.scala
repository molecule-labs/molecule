package molecule.benchmarks.core

import mbench.benchmark._
import molecule.examples.core.ThreadRing.run
import molecule.platform.Platform

object ThreadRing {

  def main(args: Array[String]): Unit = {
    val ns = Seq(500000, 1000000)
    val throughput = Column.throughput(Label[Int]("msg"))
    val benchmark = Benchmark("thread-ring", ns, Label("N"), TableReporter, warmups = 2, runs = 5).add(throughput)

    val config = Config.runtime[Platform](Platform("thread-ring", 2), _.shutdown())

    val table = benchmark(config, Test.runtime[Platform, Int]("thread-ring", run(_, 503, _)))
    println(table)
  }

  // Intel Core 2 Duo CPU P8400 2.24 Ghz: -server
  // Not compiled with -optimize:
  // [N=500000] -> [Time=.345s][tvar=11.190%][throughput=1449275.362msg/s]
  // [N=1000000] -> [Time=.684s][tvar=1.360%][throughput=1461988.304msg/s]
  // Compiled with -optimize:???
  // [N=500000] -> [Time=.367s][tvar=4.236%][throughput=1362397.820msg/s]
  // [N=1000000] -> [Time=.736s][tvar=1.178%][throughput=1358695.652msg/s]

}