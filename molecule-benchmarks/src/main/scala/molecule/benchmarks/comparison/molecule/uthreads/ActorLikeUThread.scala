package molecule
package benchmarks.comparison.molecule.uthreads

import platform.{ Platform, Executor, UThreadFactory, UThread }

/**
 * Thread that submits all its tasks sequentially to the underlying executor
 * like Scala actors.
 */
object ActorLikeUThread extends UThreadFactory {

  @inline private[this] final def wrap(task: => Unit): Runnable = new Runnable { def run() = task }

  def apply(platform: Platform, executor: Executor): UThread = new UThreadImpl(platform, executor)

  private[this] final class UThreadImpl(val platform: Platform, executor: Executor) extends UThread {

    final class Task(task: => Unit) {
      final var next: Task = _
      def run() = task
    }

    private[this] final val m = new utils.Mutex()

    private[this] final var last: Task = null
    @volatile private[this] var first: Task = null

    private[this] final object TrampolineTask extends Runnable {

      def run(): Unit = {

        try { first.run() } catch {
          case t =>
            System.err.println("UTHREAD EXCEPTION:" + t)
            System.err.println(t.getStackTraceString)
            return ()
        }

        var next = first.next

        if (next eq null) {
          m.spinLock()
          next = first.next
          if (next eq null) {
            first = null
            last = null
            m.unlock()
          } else {
            m.unlock()
            first = next
            executor.execute(TrampolineTask)
          }
        } else {
          first = next
          executor.execute(TrampolineTask)
        }

      }
    }

    private[this] final def enqueue(task: => Unit): Boolean = {
      val t = new Task(task)
      m.spinLock()
      if (last eq null) { // was empty
        last = t
        m.unlock()
        first = t
        true
      } else {
        last.next = t
        last = t
        m.unlock()
        false
      }
    }

    def submit(task: => Unit) =
      if (enqueue(task))
        executor.execute(TrampolineTask)

  }
}
