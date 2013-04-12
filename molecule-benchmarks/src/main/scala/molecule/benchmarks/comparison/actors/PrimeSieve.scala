package molecule
package benchmarks.comparison.actors

import scala.actors._
import scala.actors.Actor._
import java.util.concurrent.{ Executor, CountDownLatch }

object PrimeSieve {

  case object EOS

  /**
   * Signal end of test
   * Latch becomes 0 when last actor receives StopMessage
   */
  var latch: CountDownLatch = _

  def countDown() { latch.countDown }

  class Filter(private[this] var prime: Int, exec: Executor) extends BenchmarkActor(exec) {
    var next: Filter = null

    def act() {

      loop {
        react {
          case (n: Int) =>
            if (next == null) {
              println(n)
              prime = n
              next = new Filter(0, exec)
              next.start()
            } else {
              if (n % prime != 0)
                next ! n
            }
          case EOS =>
            if (next == null) {
              println("EOS")
              countDown
            } else {
              next ! EOS
            }
            exit()
        }
      }
    }
  }

  def run(executor: Executor, N: Int): Unit = {

    latch = new CountDownLatch(1)

    // create ring and hook them up
    val first = new Filter(0, executor)
    first.start
    var i = 2
    while (i <= N) {
      first ! i
      i += 1
    }
    first ! EOS
    latch.await
  }

  def main(args: Array[String]): Unit = {
    val executor = Executors.forkJoin(10)
    run(executor, 100000)
    executor.shutdownNow()
  }

}
