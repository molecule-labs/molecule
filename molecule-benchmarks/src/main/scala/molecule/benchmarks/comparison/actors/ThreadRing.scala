package molecule
package benchmarks.comparison.actors

import scala.actors.Actor
import scala.actors.Actor._
import java.util.concurrent.{ Executor, CountDownLatch }

object ThreadRing {

  /**
   * Signal end of test
   * Latch becomes 0 when last actor receives StopMessage
   */
  @volatile
  var latch: CountDownLatch = _

  def countDown() { latch.countDown }

  import scala.actors.SchedulerAdapter
  //  import java.util.concurrent.Executors
  //  import java.util.concurrent.ExecutorService

  class Component(label: Int, executor: Executor) extends BenchmarkActor(executor) {
    var next: Component = null

    def act() {
      link(next)
      trapExit = true

      loop {
        react {
          case 0 =>
            println(label)
            exit
          case (n: Int) =>
            next ! n - 1
          case scala.actors.Exit(s, m) =>
            countDown
            exit()
        }
      }
    }

  }

  def run(executor: Executor, size: Int, N: Int): Unit = {

    latch = new CountDownLatch(size - 1)

    // create ring and hook them up
    val first = new Component(1, executor)
    val last = List.range(2, size + 1).foldLeft(first) {
      case (p, n) =>
        val next = new Component(n, executor)
        p.next = next;
        p.start;
        next
    }
    last.next = first
    last.start

    first ! N
    latch.await
  }

  def main(args: Array[String]): Unit = {
    val executor = Executors.forkJoin(2)
    run(executor, 503, 100000)
  }

}
