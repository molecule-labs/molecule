package molecule
package benchmarks.comparison.actors

import scala.actors.Actor
import scala.actors.Actor._
import java.util.concurrent.{ Executor, CountDownLatch }

object PingPong {

  /**
   * Signal end of test
   * Latch becomes 0 when last actor receives StopMessage
   */
  @volatile
  var latch: CountDownLatch = _

  def countDown() { latch.countDown() }

  case class StopMessage()

  import scala.actors.SchedulerAdapter

  case object Ping
  case object Pong
  case object Stop

  class Ping(count: Int, pong: Actor, executor: Executor) extends BenchmarkActor(executor) {

    def act() {
      trapExit = true
      link(pong)
      var pingsLeft = count
      pong ! Ping

      loop {
        react {
          case Pong =>
            if (pingsLeft > 0) {
              pingsLeft -= 1
              pong ! Ping
            } else {
              pong ! Stop
            }
          case scala.actors.Exit(s, m) =>
            Console.println("done")
            countDown
            exit
        }
      }
    }
  }

  class Pong(executor: Executor) extends BenchmarkActor(executor) {

    def act() {
      loop {
        react {
          case Ping =>
            sender ! Pong
          case Stop =>
            exit
        }
      }
    }
  }

  def run(executor: Executor, cycles: Int, threads: Int): Unit = {
    latch = new CountDownLatch(threads)

    (1 to threads) foreach { _ =>
      val pong = new Pong(executor).start()
      val ping = new Ping(cycles, pong, executor).start()
    }

    latch.await
  }

  def main(args: Array[String]): Unit = {
    val threads = 8
    val executor = Executors.forkJoin(threads)
    run(executor, threads, 100000)
    executor.shutdown()
  }

}
