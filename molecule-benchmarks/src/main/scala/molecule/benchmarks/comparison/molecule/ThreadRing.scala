package molecule.benchmarks.comparison.molecule

object ThreadRing {

  import molecule.examples
  import examples.core.{ ThreadRing => CoreRing }
  import examples.io.ring.{ ThreadRingBetter => IORing }
  import molecule.platform.Platform
  import molecule.benchmarks.comparison.common.CThreadRing.Config

  /**
   * The setup consists in the platform and the initial value passed to the ring
   */
  type Setup = (Platform, Int)

  def runCore(platform: Platform, config: Config): Unit =
    CoreRing.run(platform, config.size, config.N)

  def runWord(platform: Platform, config: Config): Unit =
    IORing.run(platform, config.size, config.N, IORing.WNode.apply)

  def runStream(platform: Platform, config: Config): Unit =
    IORing.run(platform, config.size, config.N, IORing.Node.apply)

  def logSchedule() {
    import uthreads.TrampolineUThreadLog
    import schedulers.FlowParallelScheduler
    import executors.TrampolineTPExecutorLog

    println("UB US EB ES")

    (1 to 3).foreach { _ =>
      val platform = Platform("ring", FlowParallelScheduler.threadPoolLog(24))
      IORing.run(platform, 2, 100, IORing.WNode.apply)
      platform.shutdown()
      println(TrampolineUThreadLog.bounceCount + " " + TrampolineUThreadLog.submitCount +
        " " + TrampolineTPExecutorLog.bounceCount + " " + TrampolineTPExecutorLog.submitCount)
      TrampolineUThreadLog.reset()
      TrampolineTPExecutorLog.reset()
    }
  }

  def main(args: Array[String]) {
    logSchedule()
  }

}