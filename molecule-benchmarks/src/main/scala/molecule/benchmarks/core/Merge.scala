package molecule.benchmarks.core

import mbench.benchmark._
import molecule._
import molecule.stream._
import molecule.platform.Platform

object Merge {

  def unbalanced(n: Int, sst: Int): IChan[Int] = {
    def stream = IChan.source(1 to n, sst)
    stream
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream) // 8
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream)
      .merge(stream) // 16
  }

  def balanced(n: Int, sst: Int): IChan[Int] = {
    def stream = IChan.source(1 to n, sst)
    def m = stream.merge(stream)
    def mm = m.merge(m)
    def mmm = mm.merge(mm)
    mmm.merge(mmm)
  }

  def run(f: Int => IChan[Int])(p: Platform, n: Int) {
    val count = p.launch(
      f(n).fold(0)((count, _) => count + 1)
    ).get_!

    assert(count == 16 * n, count + "!=" + (16 * n))
    println("ok")
  }

  def manyToOne(n: Int, sst: Int): IChan[Int] = {
    def stream = IChan.source(1 to n, sst)
    val (i, mkO) = channel.ManyToOne.mk[Int](1)
    (1 to n).foreach { _ => bridge(stream, mkO()) }
    i
  }

  def runManyToOne(f: Int => IChan[Int])(p: Platform, n: Int) {
    p.launch(
      f(n).take(16 * n).connect(OChan.Void[Int])
    ).get_!

    println("ok")
  }

  def main(args: Array[String]) {
    val N = 50000
    val config = Config.runtime[Platform](Platform("merge"), _.shutdown())

    val benchmark = Benchmark("merge", Seq(N), Label("N"), TableReporter, warmups = 2, runs = 5)
    val table1 = benchmark(config, Test.runtime("unbalanced", run(unbalanced(_, 1))))
    val table2 = benchmark(config, Test.runtime("balanced", run(balanced(_, 1))))
    val table3 = benchmark(config, Test.runtime("manyToOne", runManyToOne(manyToOne(_, 1))))
    println(table1)
    println(table2)
    println(table3)
  }

  // Intel Core 2 Duo CPU P8400 2.24 Ghz: -server
  // Java HotSpot(TM) Server VM - 1.6.0_22-b04 - 17.1-b03
  // inbalanced::
  //merge:unbalanced::
  //[N=50000] -> [time=1.938s][cvar=5.309%]
  //merge:balanced::
  //[N=50000] -> [time=2.422s][cvar=7.297%]
  //merge:manyToOne::
  //[N=50000] -> [time=1.244s][cvar=3.673%]

}