package molecule
package channel
package impl

/**
 * Cache the result of a result channel such that it can be read
 * multiple times (sequentially).
 *
 * Important note: Caching messages violates uniqueness of references. Therefore, one
 * must ensure that only "Pure" messages are cached using this mechanism or be
 * careful when poisoning the channel.
 */
private class CachedRIChan[A: Message] private (richan: RIChan[A]) extends RIChan[A] { lock =>

  private sealed abstract class State
  private case object INIT extends State
  private case class SET(a: A) extends State
  private case class READING(reader: (A => Unit, Signal => Unit)) extends State
  private case class POISONED(signal: Signal) extends State

  private[this] final var STATE: State = INIT

  private def done(e: Either[Signal, A]): Unit = {
    val action: () => Unit = lock.synchronized {
      STATE match {
        case READING(reader) =>
          e match {
            case Right(a) =>
              STATE = SET(a)
              () => reader._1(a)
            case Left(signal) =>
              STATE = POISONED(signal)
              () => reader._2(signal)
          }
        case POISONED(signal) =>
          e match {
            case Right(a) =>
              () => Message[A].poison(a, signal)
            case Left(signal) =>
              utils.NOOP0
          }
        case _ =>
          val s = "Reached an invalid state in cached RIChan:" + STATE
          System.err.println(s)
          val e = new Error(s)
          STATE = POISONED(Signal(e))
          throw e
      }
    }
    action()
  }

  def read(success: A => Unit, failure: Signal => Unit): Unit = {
    val action: () => Unit = lock.synchronized {
      STATE match {
        case INIT =>
          STATE = READING((success, failure))
          richan.read(a => done(Right(a)), signal => done(Left(signal)))
          utils.NOOP0
        case SET(a) =>
          () => success(a)
        case READING(reader) => // see `select`
          STATE = READING((success, failure))
          utils.NOOP0
        case POISONED(signal) =>
          () => failure(signal)
      }
    }
    action()
  }

  def poison(signal: Signal) = {
    val action: () => Unit = lock.synchronized {
      STATE match {
        case INIT =>
          STATE = POISONED(signal)
          utils.NOOP0
        case SET(a) =>
          STATE = POISONED(signal)
          () => Message[A].poison(a, signal)
        case READING(reader) =>
          STATE = POISONED(signal)
          () => reader._2(signal)
        case POISONED(signal) =>
          utils.NOOP0
      }
    }
    action()
  }
}

private[channel] object CachedRIChan {
  def apply[A: Message](richan: RIChan[A]): RIChan[A] =
    if (richan.isInstanceOf[CachedRIChan[_]])
      richan
    else
      new CachedRIChan(richan)
}
