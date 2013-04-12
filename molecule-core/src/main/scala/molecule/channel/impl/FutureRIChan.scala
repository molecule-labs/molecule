package molecule
package channel
package impl

import java.util.concurrent.{ Future, TimeUnit }

/**
 * Result channel that can be both used either an AsynchronousFuture, an RIChan or
 * as a standard Java Future.
 *
 * As opposed to standards RIChan, which are lazy (i.e. they schedule a result only when someone reads on them),
 * a FutureIChan has already scheduled the transformations and will store the result in
 * an internal buffer until someone comes to pick it up.
 */
private final class FutureRIChan[A: Message] private () extends RIChan[A] with Future[A] {
  private sealed abstract class State
  private case object PENDING extends State
  private case class SET(a: A) extends State
  private case object BLOCKING extends State
  private case class READING(k: A => Unit, ksig: Signal => Unit) extends State
  private case class POISONED(signal: Signal) extends State

  private[this] final var STATE: State = PENDING

  def set(a: A): Unit = {
    val action: () => Unit = synchronized {
      STATE match {
        case PENDING =>
          STATE = SET(a)
          utils.NOOP0
        case BLOCKING =>
          STATE = SET(a)
          notify()
          utils.NOOP0
        case READING(k, _) =>
          STATE = SET(a)
          () => k(a)
        case POISONED(signal) =>
          () => Message[A].poison(a, signal)
        case s: SET =>
          val e = new IllegalStateException("Illegal state exception in Future")
          () => throw e
      }
    }
    action()
  }

  def poison(signal: Signal): Unit = {
    val action: () => Unit = synchronized {
      STATE match {
        case PENDING =>
          STATE = POISONED(signal)
          utils.NOOP0
        case BLOCKING =>
          STATE = POISONED(signal)
          notify()
          utils.NOOP0
        case READING(_, ksig) =>
          STATE = POISONED(signal)
          () => ksig(signal)
        case p: POISONED =>
          utils.NOOP0
        case s: SET =>
          val e = new IllegalStateException("Illegal state exception in Future")
          () => throw e
      }
    }
    action()
  }

  def read(k: A => Unit, ksig: Signal => Unit): Unit = {
    val action: () => Unit = synchronized {
      STATE match {
        case PENDING =>
          STATE = READING(k, ksig)
          utils.NOOP0
        case BLOCKING =>
          val e = new IllegalStateException("Cannot read a future while another thread is blocking on it")
          () => throw e
        case r: READING =>
          () => throw new ConcurrentReadException
        case POISONED(signal) =>
          () => ksig(signal)
        case SET(a) =>
          () => k(a)
      }
    }
    action()
  }

  def cancel(mayInterruptIfRunning: Boolean): Boolean = { poison(EOS); true }

  def get(): A = {
    synchronized {
      STATE match {
        case PENDING =>
          STATE = BLOCKING
        case BLOCKING =>
          throw new IllegalStateException("Another thread is already waiting on this future")
        case r: READING =>
          throw new IllegalStateException("Can't block on a future that is already read asynchronously")
        case POISONED(signal) =>
          Signal.throwException(signal)
        case SET(a) =>
          return a
      }
      while (STATE == BLOCKING)
        wait()
    }

    STATE match {
      case POISONED(signal) =>
        Signal.throwException(signal)
      case SET(a) =>
        return a
      case _ =>
        throw new Error("Sth terribly wrong hapenned while getting the result")
    }
  }

  /**
   * Not implemented. Use readWithin instead
   */
  def get(timeout: Long, unit: TimeUnit): A =
    throw new UnsupportedOperationException()

  def isCancelled: Boolean = synchronized {
    STATE.isInstanceOf[POISONED]
  }

  def isDone: Boolean = synchronized {
    STATE.isInstanceOf[SET] || STATE.isInstanceOf[POISONED]
  }
}

private[channel] object FutureRIChan {

  /**
   * Create a FutureRIChan that schedules the transformations applied
   * to the original channel inside a given executor.
   *
   * @param exec the executor used to schedule transformations.
   * @return a FutureRIChan.
   */
  private[channel] def apply[A: Message](richan: RIChan[A]): RIChan[A] with Future[A] =
    if (richan.isInstanceOf[FutureRIChan[_]])
      richan.asInstanceOf[FutureRIChan[A]]
    else {
      val f = new FutureRIChan[A]()
      richan.read(f.set, f.poison)
      f
    }

}