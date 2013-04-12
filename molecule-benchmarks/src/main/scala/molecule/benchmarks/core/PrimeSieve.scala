package molecule.benchmarks.core

import mbench.benchmark._
import molecule.examples.core.PrimeSieve.run
import molecule.platform.Platform

object PrimeSieve {

  def main(args: Array[String]): Unit = {
    val ns = Seq(100000, 200000)
    val config = Config.runtime[Platform](Platform("prime-sieve"), _.shutdown())
    val benchmark = Benchmark("prime-sieve", ns, Label("N"), TableReporter, warmups = 2, runs = 5)
    val table = benchmark(config, Test.runtime("prime-sieve", run))
    println(table)
  }

  // Intel Core 2 Duo CPU P8400 2.24 Ghz (compiled with -optimize): -server Eclipse
  // - primes execution times (Java HotSpot(TM) Server VM - 1.6.0_22-b04 - 17.1-b03):
  // [N=100000] -> [time=.709s][cvar=8.167%]
  // [N=200000] -> [time=1.861s][cvar=3.255%]

}