package molecule.benchmarks.comparison.molecule

object PrimeSieve {
  import molecule.platform.Platform
  import molecule.stream._
  import molecule.channel.Console
  import molecule.benchmarks.comparison.common.CPrimeSieve.Config

  /**
   * Setup expected by this test:
   * - a platform,
   * - the segment size threshold (SST) use to configure the generator
   * - the complexity cutoff thresholds (CCT) used to configure the number
   * of filters that must be fused together.
   */
  type Setup = (Platform, (Int, Int))

  object Core {
    def sieve(stream: IChan[Int]): IChan[Int] =
      stream.flatMap { (p, next) => p :: sieve(next.filter(_ % p != 0)) }

    val allPrimes = sieve(IChan.from(2))

    def primes(max: Int, sst: Int): IChan[Int] = sieve(IChan.source(2 to max, sst))

    def run(platform: Platform, max: Int, sst: Int) {

      val log = Console.logOut[Int]("log")

      val stream = primes(max, sst) connect log

      platform.launch(stream).get_!
    }
  }

  object IO {
    import molecule.EOS
    import molecule.io._

    object PrimeSieve extends ProcessType1x1[Int, Int, Unit] {

      def main(ints: Input[Int], log: Output[Int]) =
        for {
          // get the initial prime or exit if none is available
          prime <- ints.read() orCatch {
            case EOS => shutdown()
          }
          // send the prime to the log channel
          _ <- log.write(prime)
          // launch next component passing the log channel as output
          _ <- handover(PrimeSieve(ints.filter(_ % prime != 0), log))
        } yield ()

      def run(platform: Platform, N: Int, sst: Int) {

        val ints = IChan.source(2 to N, sst)

        /**
         * Logger channel prints the output on stdout.
         */
        val log = Console.logOut[Int]("log") //.smap[Int, Int](0)((i, n) => (i+1, i+":"+n))

        platform.launch(PrimeSieve(ints, log)).get_!()

      }

    }
  }

  def runCore(platform: Platform, config: (Config, (Int, Int))): Unit = {
    val (Config(n), (sst, cct)) = config

    val occt = Platform._complexityCutoffThreshold
    Platform._complexityCutoffThreshold = cct

    Core.run(platform, n, sst)

    Platform._complexityCutoffThreshold = occt
  }

  def runIO(platform: Platform, config: (Config, (Int, Int))): Unit = {
    val (Config(n), (sst, cct)) = config

    val occt = Platform._complexityCutoffThreshold
    Platform._complexityCutoffThreshold = cct

    IO.PrimeSieve.run(platform, n, sst)

    Platform._complexityCutoffThreshold = occt
  }

}