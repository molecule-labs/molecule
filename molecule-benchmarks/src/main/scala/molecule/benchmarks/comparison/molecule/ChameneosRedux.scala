package molecule.benchmarks.comparison.molecule

object ChameneosRedux {

  import molecule.examples
  import examples.io.chameneos.{ ChameneosRedux => IOChameneosRedux }
  import examples.core.{ ChameneosRedux => CoreChameneosRedux }
  import molecule.platform.Platform
  import molecule.benchmarks.comparison.common.CChameneosRedux.Config

  def runIO(platform: Platform, config: (Config, Int)): Unit = {
    val (common, sst) = config
    IOChameneosRedux.run(platform, platform, common.nbChameneos, common.nbMeetings, sst)
  }

  def runCore(platform: Platform, config: (Config, Int)): Unit = {
    val (common, sst) = config
    CoreChameneosRedux.run(platform, platform, common.nbChameneos, common.nbMeetings, sst)
  }

  def main(args: Array[String]) {
    logSchedule(Seq(500), 100000)
    //logSchedule(Seq(2), 100000)
  }

  def logSchedule(chameneos: Seq[Int], nbMeetings: Int) {
    import uthreads.TrampolineUThreadLog
    import executors.{ TrampolineTPExecutorLog => TrampolineExecutorLog }

    println("N UB US EB ES")

    chameneos.foreach { N =>
      (1 to 3).foreach { _ =>
        val platform = Platform("chameneos", schedulers.FlowParallelScheduler.threadPoolLog(24))
        IOChameneosRedux.run(platform, platform, N, nbMeetings, 0)
        platform.shutdown()
        println(N + " " + TrampolineUThreadLog.bounceCount + " " + TrampolineUThreadLog.submitCount +
          " " + TrampolineExecutorLog.bounceCount + " " + TrampolineExecutorLog.submitCount)
        val uBounce = TrampolineUThreadLog.bounceCount.get()
        val uSubmit = TrampolineUThreadLog.submitCount.get()
        val eBounce = TrampolineExecutorLog.bounceCount.get()
        val eSubmit = TrampolineExecutorLog.submitCount.get()

        val tasks = uBounce + uSubmit
        println("Total: " + tasks + " tasks")
        println("L1 trampoline: " + (uBounce.toDouble * 100 / tasks) + "%")
        println("L2 trampoline: " + (eBounce.toDouble * 100 / tasks) + "%")
        println("Total trampoline: " + ((uBounce + eBounce).toDouble * 100 / tasks) + "%")

        TrampolineUThreadLog.reset()
        TrampolineExecutorLog.reset()
      }
    }
  }

}