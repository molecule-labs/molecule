package molecule.benchmarks.comparison.molecule

import mbench.benchmark._
import molecule.platform.{ Platform, SchedulerFactory }

object SchedulerConfig {

  private def apply(schedulerName: String, scheduler: Int => SchedulerFactory): RuntimeConfig[Int, Platform] = {

    Config.runtime[Int, Platform](
      schedulerName,
      threads => Platform(schedulerName, schedulerFactory = scheduler(threads)),
      platform => platform.shutdownNow()
    )

  }

  import schedulers._

  val actorLike = apply(Molecule.actorLike, ActorLikeScheduler.forkJoin)

  val fptp = apply(Molecule.fptp, FlowParallelScheduler.threadPool)

  val fpfj = apply(Molecule.fpfj, FlowParallelScheduler.forkJoin)

  val wctp = apply(Molecule.wctp, WorkConservingScheduler.threadPool)

  val wcfj = apply(Molecule.wcfj, WorkConservingScheduler.forkJoin)

  def apply(schedulerName: String): RuntimeConfig[Int, Platform] = schedulerName match {
    case Molecule.actorLike => actorLike
    case Molecule.fptp => fptp
    case Molecule.fpfj => fpfj
    case Molecule.wctp => wctp
    case Molecule.wcfj => wcfj
    case _ => sys.error("Scheduler " + schedulerName + " not supported")
  }

}