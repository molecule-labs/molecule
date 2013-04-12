package molecule.benchmarks.comparison

trait Benchmarks {

  import mbench.benchmark._
  import mbench.gnuplot._
  import common._

  def threadRing(
    benchmark: Benchmark[Int, CThreadRing.Config, DatFile],
    config: StaticConfig[CThreadRing.Config]): Seq[DatFile]

  def pingPong(
    benchmark: Benchmark[Int, Int, DatFile],
    config: StaticConfig[Int]): Seq[DatFile]

  def chameneosRedux(
    benchmark: Benchmark[Int, CChameneosRedux.Config, DatFile],
    config: StaticConfig[CChameneosRedux.Config]): Seq[DatFile]

  def primeSieve(
    benchmark: Benchmark[Int, CPrimeSieve.Config, DatFile],
    config: StaticConfig[CPrimeSieve.Config]): Seq[DatFile]

}