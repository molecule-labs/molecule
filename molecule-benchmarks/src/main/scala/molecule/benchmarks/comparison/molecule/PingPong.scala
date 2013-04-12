package molecule.benchmarks.comparison.molecule

object PingPong {
  import molecule._
  import Molecule._
  import molecule.io._

  object Ping extends ProcessType2x1[Int, Unit, Unit, Unit] {
    override def name = "Ping"

    def repeat(n: Int)(action: IO[Unit]): IO[Unit] =
      if (n == 0) ioLog("done") else action >> repeat(n - 1)(action)

    def main(cfg: Input[Int], in: Input[Unit], out: Output[Unit]): IO[Unit] = {

      cfg.read() >>\ { count =>
        repeat(count) {
          out.write(()) >> in.read()
        }
      }
    }
  }

  object Pong extends ProcessType1x1[Unit, Unit, Unit] {
    override def name = "Pong"

    def main(in: Input[Unit], out: Output[Unit]): IO[Unit] =
      in.connect(out) >> IO()

  }

  import molecule.platform.Platform
  import molecule.channel.{ Chan, RIChan }

  def launch(platform: Platform, cycles: Int): RIChan[Unit] = {
    val (i1, o1) = Chan.mk[Unit]()
    val (i2, o2) = Chan.mk[Unit]()

    platform.launch(Pong(i1, o2))
    platform.launch(Ping(cycles.asI, i2, o1))
  }

  def run(platform: Platform, cycles: Int, threads: Int) {
    (1 to threads).map(_ => launch(platform, cycles)).foreach { _.get_!() }
  }

  def main(args: Array[String]) {
    val threads = 8
    val platform = Platform("ping-pong", nbThreads = threads)
    run(platform, 100000000, threads)
    platform.shutdown()
  }
}