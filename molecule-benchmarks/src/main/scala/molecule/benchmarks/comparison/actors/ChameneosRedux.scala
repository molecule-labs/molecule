/**
 * From: http://lampsvn.epfl.ch/svn-repos/scala/scala/branches/translucent/docs/examples/actors/chameneos-redux.scala
 *
 * The Computer Language Benchmarks Game
 * <p/>
 * URL: [http://shootout.alioth.debian.org/]
 * <p/>
 * Contributed by Julien Gaugaz.
 * <p/>
 * Inspired by the version contributed by Yura Taras and modified by Isaac Gouy.
 */
package molecule
package benchmarks.comparison.actors

object ChameneosRedux {

  import scala.actors._
  import scala.actors.Actor._
  import java.util.concurrent.{ Executor, CountDownLatch }
  import scala.actors.SchedulerAdapter

  var latch: CountDownLatch = _

  def countDown() { latch.countDown }

  abstract class Colour
  case object RED extends Colour
  case object YELLOW extends Colour
  case object BLUE extends Colour
  case object FADED extends Colour

  val colours = List(BLUE, RED, YELLOW)

  case class Meet(colour: Colour)
  case class Change(colour: Colour)
  case class MeetingCount(count: Int)

  class Mall(var n: Int, nbChameneos: Int, exec: Executor) extends BenchmarkActor(exec) {

    var waitingChameneo: Option[OutputChannel[Any]] = None

    var startTime: Long = 0L

    start()

    def startChameneos(): Unit = {
      var i = 0

      while (i < nbChameneos) {
        Chameneo(this, colours(i % 3), i, exec).start()
        i = i + 1
      }
    }

    def act() {
      var sumMeetings = 0
      var numFaded = 0

      loop {
        react {
          case MeetingCount(i) => {
            numFaded = numFaded + 1
            sumMeetings = sumMeetings + i

            if (numFaded == nbChameneos) {
              //println(numFaded)
              println(sumMeetings)
              latch.countDown()
              exit()
            }
          }

          case msg @ Meet(c) => {
            if (n > 0) {
              waitingChameneo match {
                case Some(chameneo) =>
                  n = n - 1
                  chameneo.forward(msg)
                  waitingChameneo = None

                case None =>
                  waitingChameneo = Some(sender)
              }
            } else {
              waitingChameneo match {
                case Some(chameneo) =>

                  chameneo ! Exit(this, "normal")

                case None =>

              }

              sender ! Exit(this, "normal")

            }
          }
        }
      }
    }
  }

  case class Chameneo(var mall: Mall, var colour: Colour, id: Int, exec: Executor) extends BenchmarkActor(exec) {

    var meetings = 0

    def act() {

      loop {

        mall ! Meet(colour)

        react {
          case Meet(otherColour) =>
            colour = complement(otherColour)

            meetings = meetings + 1
            sender ! Change(colour)

          case Change(newColour) =>

            colour = newColour
            meetings = meetings + 1

          case Exit(_, _) =>

            colour = FADED
            sender ! MeetingCount(meetings)
            exit()

        }
      }
    }

    def complement(otherColour: Colour): Colour = {

      colour match {
        case RED => otherColour match {
          case RED => RED
          case YELLOW => BLUE
          case BLUE => YELLOW
          case FADED => FADED
        }
        case YELLOW => otherColour match {
          case RED => BLUE
          case YELLOW => YELLOW
          case BLUE => RED
          case FADED => FADED
        }
        case BLUE => otherColour match {
          case RED => YELLOW
          case YELLOW => RED
          case BLUE => BLUE
          case FADED => FADED
        }
        case FADED => FADED
      }
    }

    override def toString() = id + "(" + colour + ")"

  }

  def run(executor: Executor, nbChameneos: Int, nbMeetings: Int): Unit = {
    latch = new CountDownLatch(1)
    val mall = new Mall(nbMeetings, nbChameneos, executor)
    mall.startChameneos
    latch.await
  }

  def main(args: Array[String]) {
    val executor = Executors.forkJoin(4)
    run(executor, 20, 600000)
    executor.shutdownNow()
  }

}
