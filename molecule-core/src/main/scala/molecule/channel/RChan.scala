/*
 * Copyright (C) 2013 Alcatel-Lucent.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * Licensed to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package molecule
package channel

import java.util.concurrent.{ Future, Executor, TimeUnit }

/**
 * "Result", "reply" or "response" input channel interface.
 *
 * Result channels are system-level input channels that deliver only
 * a single message followed by the EOS. They obey channel semantics in that
 * they cannot be read concurrently and they deliver messages only
 * once - a result channel that is read a second time will deliver the
 * EOS signal.
 *
 * The computations associated to a result channel, and hence
 * transformations like `map` or `flatMap`, are only fired lazyliy when
 * a process attempts to read the result from the channel. Transformations
 * featuring side-effects can be executed even if no one is
 * interested in the result by calling the `fire()` method. This method
 * returns a new result channel that consumes the result after the
 * transformations have been applied and then stores it internally
 * in an intermediate buffer such that it can still be consumed later.
 *
 * In all situations, the continuations or the transformation functions
 * applied to a result channel are carried by default inside the
 * thread that produces the result. To improve reactiveness and/or isolate
 * concurrent computations from each other, it is prefereable to free as
 * soon as possible the thread that produces results and offload the
 * computation of transformations to the thread that consumes the result.
 * The `dispatchTo` method, if it is called immediately after a future
 * is created, will dipatch any subsequent continuation or transformation
 * to the standard `juc.Executor` that it is passed as parameter. For example,
 * this could be either a [[molecule.platform.Platform]] or a
 * [[molecule.platform.UThread]], which both inherit from the `Executor`
 * interface. In the first case, continuations will be carried inside a new
 * user-level thread created by the target platform. In the second case,
 * the continuations will be pinned down to an existing user-level thread.
 *
 * Note that in case a message is pure, the result of a result channel
 * can be cached for multiple (sequential) reads using the
 * `cache()` method. Alternatively, for
 * interoperability with Java, a result can also be wrapped
 * inside a standard `juc.Future` by invoking the `future()` method
 * on result channels (this method is provided via an implicit conversion
 * to `RIChanWithFuture` in the companion object).
 *
 * @tparam A the type of the message returned by the channel
 */
abstract class RIChan[+A] extends IChan[A] { outer =>

  /**
   * Read a result asynchronously using continuations for success and
   * failure cases.
   *
   * @param success continuation invoked in case of success.
   * @param failure continuation invoked in case of failure.
   * @return unit
   */
  def read(success: A => Unit, failure: Signal => Unit): Unit

  def read(k: (Seg[A], IChan[A]) => Unit): Unit =
    read(a => k(Seg(a), IChan.empty(EOS)), signal => k(Seg(), IChan.empty(signal)))

  /**
   * Creates a new result channel whose transformations will be executed in the
   * context of another executor. The executor might be a Platform or a
   * user-level thread (UThread), which both implement the juc.Executor interface.
   *
   * @param executor the executor that will execute the subsequent transformations.
   * @return a result channel whose continuations will be invoked in the context
   * of the executor.
   */
  final def dispatchTo(executor: Executor): RIChan[A] = new RIChan[A] {

    def read(success: A => Unit, failure: Signal => Unit): Unit =
      outer.read(
        a => executor.dispatch(success(a)),
        s => executor.dispatch(failure(s)))

    def poison(signal: Signal): Unit =
      outer.poison(signal)
  }

  /**
   * React to a result asynchronously using continuations for success
   * and failure cases.
   *
   * @param success continuation invoked in case of success.
   * @param failure continuation invoked in case of failure.
   * @return unit
   */
  def onComplete(success: A => Unit, failure: Signal => Unit): Unit =
    read(success, failure)

  /**
   * Fallback to an alternative result channel if this channel raises a signal
   * instead of returning a result.
   *
   * @param recover the partial function invoked if a signal is raised.
   * @return a new result channel.
   */
  def orCatch[B >: A](recover: PartialFunction[Signal, RIChan[B]]): RIChan[B] = new RIChan[B] {
    def read(success: B => Unit, failure: Signal => Unit): Unit =
      outer.read(success, signal =>
        if (recover.isDefinedAt(signal))
          recover(signal).read(success, failure)
        else failure(signal)
      )
    def poison(signal: Signal): Unit = outer.poison(signal)
  }

  /**
   * Read the result of a result channel within the specified timeout. If the
   * result is no available within the specified timeout, this channel will
   * be automatically poisoned.
   *
   * @param delay    the time from now to delay execution.
   * @param unit     the time unit of the delay parameter.
   * @return some result if the result becomes available before the timeout,
   *         else none.
   */
  def readWithin(delay: Long, unit: TimeUnit)(implicit ma: Message[A]): RIChan[Option[A]] =
    (this or Timer.timeout(delay, unit)) flatMap {
      case Left(a) => RIChan.success(Some(a))
      case Right(_) => RIChan.success(None)
    }

  /**
   * Try to read the result of a result channel within the specified timeout. If
   * the result is not available before the specified timeout, a new result channel
   * is returned, which can be used to retrieve the result again later.
   *
   * @param delay    the time from now to delay execution.
   * @param unit     the time unit of the delay parameter.
   * @return Either the result if it becomes available before the timeout, or a new result
   *                channel, which can be read a second time later.
   */
  def tryReadWithin(delay: Long, unit: TimeUnit)(implicit ma: Message[A]): RIChan[Either[RIChan[A], A]] =
    (this select Timer.timeout(delay, unit)) flatMap {
      case Left((a, timer)) =>
        timer.poison(EOS)
        RIChan.success(Right(a))
      case Right((_, richan)) =>
        RIChan.success(Left(richan))
    }

  /**
   * Cache the message received on the underlying channel such that the same result
   * can be read multiple times (sequentially).
   *
   * Important note: Caching messages violates uniqueness of references. Therefore, one
   * must ensure that only "Pure" messages are cached using this mechanism or be
   * careful when poisoning the resulting channel.
   *
   * @tparam A the type of the message produced by the channel.
   * @param ri the channel whose result must be cached.
   * @return a result channel that can be read multiple times.
   */
  def cache()(implicit ma: Message[A]): RIChan[A] =
    impl.CachedRIChan(this)

  /**
   * Execute all the transformations stacked up on this result channel, and then
   * cache the result into a new result channel. This method ensures that all
   * side-effects performed by transformations applied on this result channel
   * are executed, even if this future is not consumed. Note that it is useless
   * to invoke this method if transformations have no side-effects.
   *
   * @return a RIChan.
   */
  def fire()(implicit m: Message[A]): RIChan[A] =
    impl.FutureRIChan(this)

  import signals.AndSignal

  /**
   * Choose the first result between the result of this channel and the one of
   * another channel.
   *
   * The new result channel created succeeds with the first successful result
   * returned by either this channel or the other channel. It fails only
   * if both channels fail. Whenever one of the result channels succeeds,
   * the other result channel is returned with the result such that one
   * can attempt to retrieve the other result a second time later.
   *
   * @param other the other result channel.
   * @return either the result of this channel or the result of the other one.
   */
  def select[B](other: RIChan[B])(implicit ma: Message[A], mb: Message[B]): RIChan[Either[(A, RIChan[B]), (B, RIChan[A])]] =
    new RIChan[Either[(A, RIChan[B]), (B, RIChan[A])]] {

      val left = RIChan.this.cache()
      val right = other.cache()

      def read(success: Either[(A, RIChan[B]), (B, RIChan[A])] => Unit, failure: Signal => Unit): Unit = {
        var FIRST: Either[Signal, Any] = null

        def wrapSuccess[C, D](f: C => Unit): C => Unit = c => {
          val action: () => Unit = synchronized {
            if (FIRST eq null) {
              FIRST = Right(None)
              () => f(c)
            } else FIRST match {
              case Left(signal) => // First failed
                () => f(c)
              case Right(_) => // First already succeeded
                utils.NOOP0
            }
          }
          action()
        }

        def wrapFailure(f: Signal => Unit): Signal => Unit = signal => {
          val action: () => Unit = synchronized {
            if (FIRST eq null) {
              FIRST = Left(signal)
              utils.NOOP0
            } else FIRST match {
              case Left(signal1) =>
                FIRST = Left(EOS)
                () => f(AndSignal(signal1, signal))
              case Right(_) =>
                utils.NOOP0
            }
          }
          action()
        }

        right.read(
          wrapSuccess(b => success(Right((b, left)))),
          wrapFailure(failure)
        )

        left.read(
          wrapSuccess(a => success(Left((a, right)))),
          wrapFailure(failure)
        )
      }

      def poison(signal: Signal): Unit = {
        left.poison(signal)
        right.poison(signal)
      }
    }

  /**
   * Choose the first result between the result of this channel and the one of
   * another channel.
   *
   * The new result channel created succeeds with the first successful result
   * returned by either this channel or the other channel. It fails only
   * if both channels fail. The other result channel will be automatically
   * poisoned once a result becomes available.
   *
   * @param other the other result channel.
   * @return either the result of this channel or the result of the other one.
   */
  def or[B](other: RIChan[B])(implicit ma: Message[A], mb: Message[B]): RIChan[Either[A, B]] =
    new RIChan[Either[A, B]] {

      def read(success: Either[A, B] => Unit, failure: Signal => Unit): Unit = {
        var FIRST: Either[Signal, Any] = null

        def wrapSuccess[C](f: C => Unit)(implicit mc: Message[C]): C => Unit = c => {
          val action: () => Unit = synchronized {
            if (FIRST eq null) {
              FIRST = Right(None)
              () => f(c)
            } else FIRST match {
              case Left(signal) => // first failed
                () => f(c)
              case Right(_) => // first already succeeded
                mc.poison(c, EOS)
                utils.NOOP0
            }
          }
          action()
        }

        def wrapFailure(f: Signal => Unit): Signal => Unit = signal => {
          val action: () => Unit = synchronized {
            if (FIRST eq null) {
              FIRST = Left(signal)
              utils.NOOP0
            } else FIRST match {
              case Left(signal1) => // first failed
                FIRST = Left(EOS)
                () => f(AndSignal(signal1, signal))
              case Right(_) =>
                utils.NOOP0
            }
          }
          action()
        }

        other.read(
          wrapSuccess(b => success(Right(b))),
          wrapFailure(failure)
        )

        outer.read(
          wrapSuccess(a => success(Left(a))),
          wrapFailure(failure)
        )
      }

      def poison(signal: Signal): Unit = {
        outer.poison(signal)
        other.poison(signal)
      }
    }

  /**
   * Return both the result of this channel and the one of another channel.
   *
   * The new channel created succeeds with both successful results or
   * fails as soon as one of the RIChan fails.
   *
   * @param other the other result channel.
   * @return a pair containing the the result of this channel and the other one.
   */
  def and[B](other: RIChan[B])(implicit ma: Message[A], mb: Message[B]): RIChan[(A, B)] = new RIChan[(A, B)] {

    def read(success: ((A, B)) => Unit, failure: Signal => Unit): Unit = {
      var FIRST: Either[Signal, Any] = null

      def wrapSuccess[C](f: (Any, C) => Unit)(implicit mc: Message[C]): C => Unit = c => {
        val action: () => Unit = synchronized {
          if (FIRST eq null) {
            FIRST = Right(c)
            utils.NOOP0
          } else FIRST match {
            case Left(signal) =>
              mc.poison(c, signal)
              utils.NOOP0
            case Right(v) =>
              FIRST = Left(EOS)
              () => f(v, c)
          }
        }
        action()
      }

      def wrapFailure(f: Signal => Unit, poison: (Any, Signal) => Unit): Signal => Unit = signal => {
        val action: () => Unit = synchronized {
          if (FIRST eq null) {
            FIRST = Left(signal)
            () => f(signal)
          } else FIRST match {
            case Left(signal) =>
              FIRST = Left(EOS)
              utils.NOOP0
            case Right(v) =>
              FIRST = Left(EOS)
              () => poison(v, signal)
          }
        }
        action()
      }

      other.read(
        wrapSuccess((first, b) => success((first.asInstanceOf[A], b))),
        wrapFailure(failure, (first, signal) => ma.poison(first.asInstanceOf[A], signal)))

      outer.read(
        wrapSuccess((first, a) => success((a, first.asInstanceOf[B]))),
        wrapFailure(failure, (first, signal) => mb.poison(first.asInstanceOf[B], signal)))
    }

    def poison(signal: Signal): Unit = {
      outer.poison(signal)
      other.poison(signal)
    }

  }

  /**
   * Map a function to the result of this channel interface.
   *
   * The thread that executes the function is defined by how this result
   * channel was created.
   *
   * @param f the function applied to the result produced by this channel
   *          in case of success.
   * @return A new channel interface obtained by applying `f` to the result
   * of this channel interface.
   */
  def map[B](f: A => B): RIChan[B] = new RIChan[B] {
    def read(success: B => Unit, failure: Signal => Unit): Unit =
      outer.read(a => success(f(a)), signal => failure(signal))
    def poison(signal: Signal): Unit = outer.poison(signal)
  }

  /**
   * Schedule another asynchronous continuation, which is a function of
   * the result of this channel.
   *
   * The thread that executes the function is defined by how this result
   * channel was created.
   *
   * @param f the function that creates a new asynchronous computation
   *   using the success result of this channel interface.
   * @return a new channel interface obtained by chaining a new computation after
   * this result is available.
   */
  def flatMap[B](f: A => RIChan[B]): RIChan[B] = new RIChan[B] {
    private[this] final var tmp: Either[Signal, RIChan[B]] = null
    def read(success: B => Unit, failure: Signal => Unit): Unit =
      outer.read(a => {
        val next = f(a)
        synchronized { tmp = Right(next) };
        next.read(success, failure)
      }, failure)
    def poison(signal: Signal): Unit = {
      outer.poison(signal)
      synchronized {
        if (tmp != null) tmp match {
          case Right(richan) => richan.poison(signal)
          case _ =>
        }
      }
    }
  }

  /**
   * BLOCK the native thread until a result is available.
   *
   * All transformations applied before the result channel (or after a FutureRIChan) was
   * was created will be applied in the same native thread than the one that blocks on
   * this result.
   *
   * @return the result.
   */
  def get_!(): A = {

    var tmp: Either[Signal, A] = null
    // Normally submission must be sequential (i.e. never more than one element)
    // but just in case we use a queue ...
    val q = new scala.collection.mutable.Queue[Runnable]

    val e: Executor = new Executor {
      def execute(r: Runnable) = outer.synchronized {
        q.enqueue(r)
        outer.notify()
      }
    }

    dispatchTo(e).onComplete(a => tmp = Right(a), signal => tmp = Left(signal))

    while (tmp == null) {
      synchronized {
        while (q.isEmpty)
          wait()
      }
      val task = q.dequeue
      task.run()
    }

    tmp.fold(signal => Signal.throwException(signal), identity)
  }
}

/**
 * Factory methods for RIChan and extra DSL support like `callcc` and `parl`
 * (see `ChameneosRedux` example in `molecule-core-examples`).
 */
object RIChan {

  /**
   * Create a result channel that returns the result of an asynchronous task that
   * is scheduled only when some process attempts to consume the result.
   *
   * Note: the executor that executes the task can be released immediately once
   * the result becomes available, independently from the transformations
   * applied to the result, by calling `dispathTo` immediately after this
   * channel is created.
   *
   * @tparam A the type of the message created by the task.
   * @param executor the executor that executes the task and produces a result.
   * @param task the task that is executed asynchronously by the executor.
   * @return a result channel that produces the result of the task
   */
  def lazyAsync[A: Message](executor: Executor)(task: => A): RIChan[A] = new RIChan[A] {
    @volatile private[this] var SIGNAL: Signal = null

    def read(success: A => Unit, failure: Signal => Unit): Unit = {
      if (SIGNAL != null) failure(SIGNAL)
      else executor.dispatch {
        if (SIGNAL != null) failure(SIGNAL)
        else try {
          val a = task
          if (SIGNAL != null) {
            Message.poison(a, SIGNAL)
            failure(SIGNAL)
          } else
            success(a)
        } catch {
          case t: Throwable => failure(Signal(t))
        }
      }
    }

    def poison(signal: Signal) = SIGNAL = signal
  }

  /**
   * Create a result channel that returns the result of an asynchronous task
   * that is scheduled immediately for execution.
   *
   * Note: the executor that executes the task can be released immediately once
   * the result becomes available, independently from the transformations
   * applied to the result, by calling `dispathTo` immediately after this
   * channel is created.
   *
   * @tparam A the type of the message created by the task.
   * @param executor the executor that executes the task and produces a result.
   * @param task the task that is executed asynchronously by the executor.
   * @return a result channel that produces the result of the task
   */
  def async[A: Message](executor: Executor)(task: => A): RIChan[A] = {
    val (ri, ro) = RChan.mk[A]()
    executor.dispatch {
      try {
        ro.success_!(task)
      } catch {
        case t: Throwable => ro.failure_!(Signal(t))
      }
    }

    ri
  }

  /**
   * Create a result channel that returns a message immediately.
   *
   * @tparam A the type of the message.
   * @param a the message returned by the channel.
   * @return a result channel that produces the message `a`
   */
  def success[A: Message](a: A): RIChan[A] = new RIChan[A] {
    def read(success: A => Unit, failure: Signal => Unit): Unit = success(a)
    def poison(signal: Signal) = Message.poison(a, signal)
  }

  /**
   * Create a result channel that fails with a signal.
   *
   * @param a the signal that indicates the type of failure.
   * @return a result channel that produces the message `a`
   */
  def failure(signal: Signal): RIChan[Nothing] = new RIChan[Nothing] {
    def read(success: Nothing => Unit, failure: Signal => Unit): Unit = failure(signal)
    def poison(signal: Signal) = ()
  }

  /**
   * Call with current continuation.
   *
   * @param call a function that takes the current continuation as argument.
   * @return a result channel that returns the parameter passed to `call`.
   */
  def callcc[A: Message](call: (A => RIChan[Nothing]) => RIChan[Nothing]): RIChan[A] = new RIChan[A] {
    @volatile private[this] var POISONED: Signal = null

    def read(success: A => Unit, failure: Signal => Unit): Unit = {
      call(a => new RIChan[Nothing] {
        def read(s: Nothing => Unit, f: Signal => Unit): Unit =
          if (POISONED eq null) success(a) else Message[A].poison(a, POISONED)
        def poison(signal: Signal): Unit = ()
      }).onComplete(utils.NOOP, utils.NOOP)
    }

    def poison(signal: Signal): Unit = { POISONED = signal }
  }

  import scala.collection.generic.CanBuildFrom

  /**
   * Consume the results of a list of result channels in parallel and return
   * them as a list. If one result channel fails, all the other results are
   * poisoned and a single signal corresponding to the exception is raised.
   *
   * @param ris a list of result channels.
   * @return the list of results. Results occur in the same as the order as
   * the channels that produced them.
   */
  def parl[A, That <: Traversable[A]](ris: Iterable[RIChan[A]])(implicit ma: Message[A], bf: CanBuildFrom[Nothing, A, That]): RIChan[That] =
    if (ris.isEmpty) {
      RIChan.success(bf().result())(Message[Traversable[A]])
    } else new RIChan[That] {

      val tot = ris.size
      var cnt = 0
      @volatile var status: Either[Signal, Array[Option[A]]] = Right(Array.fill(tot)(None))

      def read(success: That => Unit, failure: Signal => Unit): Unit = {
        import scala.collection.immutable.SortedMap

        val ka: Int => A => Unit = { i =>
          a => {
            val action: () => Unit = synchronized {
              status match {
                case Right(results) =>
                  if (results(i).isDefined) {
                    // Internal data race issue (this is bad)
                    System.err.println("collision!!!" + i + " -> " + a)
                    System.err.println(new Exception().getStackTraceString)
                    System.exit(0)
                  }
                  results(i) = Some(a)
                  cnt += 1
                  if (cnt == tot) {
                    status = Left(Signal("parl over"))
                    () => {
                      val builder = bf()
                      var i = 0
                      while (i < tot) {
                        builder += results(i).get
                        results(i) = null
                        i += 1
                      }
                      success(builder.result)
                    }
                  } else utils.NOOP0

                case Left(signal) => // fatal
                  () => ma.poison(a, signal)
              }
            }
            action()
          }
        }

        val error: Signal => Unit = { signal =>
          val action: () => Unit = synchronized {
            status match {
              case Right(results) =>
                status = Left(signal)
                () => {
                  results.foreach {
                    case Some(a) => ma.poison(a, signal)
                    case None =>
                  }
                  failure(signal)
                }
              case Left(signal) =>
                // We already came here
                () => Unit
            }
          }
          action()
        }

        ris.zipWithIndex.foreach {
          case (richan, i) =>
            richan.read(ka(i), error)
        }
      }

      def poison(signal: Signal): Unit = {
        val action: () => Unit = synchronized {
          status match {
            case Right(results) =>
              status = Left(signal)
              () => {
                results.foreach {
                  case Some(a) => ma.poison(a, signal)
                  case None =>
                }
              }
            case Left(signal) =>
              // Already poisoned
              () => Unit
          }
        }
        action()
      }
    }

  private[this] final class RIChanImpl[A](chan: IChan[A]) extends RIChan[A] {

    override def read(k: (Seg[A], IChan[A]) => Unit): Unit =
      chan.read((seg, ichan) => k(seg, ichan))

    def read(k: A => Unit, ksig: Signal => Unit): Unit =
      chan.read {
        case (Seg(a), _) => k(a)
        case (NilSeg, IChan(signal)) => ksig(signal)
        case (seg, ichan) => ksig(Signal(new Error("Illegal result")))
      }

    def poison(signal: Signal): Unit =
      chan.poison(signal)
  }

  /**
   * Create a result input channel.
   *
   * Note: This is only safe to create result channel like this if the other
   * end is guaranteed to output a single result.
   *
   * @param ichan a standard input channel.
   * @return a result input channel that reads its result on the standard channel.
   */
  private[molecule] def apply[A](ichan: IChan[A]): RIChan[A] =
    if (ichan.isInstanceOf[RIChan[_]])
      ichan.asInstanceOf[RIChan[A]]
    else
      new RIChanImpl(ichan)

  /**
   * Class that enriches a RIChan with a `future` methods.
   *
   * (The implicit conversion is located in the main Molecule object
   * imported by end-users.)
   *
   * @tparam A the type of the message returned by the channel
   */
  class RIChanWithFuture[A: Message](richan: RIChan[A]) {

    /**
     * Convert a result channel to a standard Future. Note that a result
     * can be already retrieved synchronously (i.e. in a blocking manner)
     * from a result channel using its `get_!` method. This is only present
     * for interoperability.
     *
     * Note: this method can not be implemented as a class member of
     * [[molecule.channel.RIChan]] because Future is invariant in
     * its type parameter.
     *
     * @return a RIChan with a Future.
     */
    def future(): RIChan[A] with java.util.concurrent.Future[A] =
      impl.FutureRIChan(richan)
  }

  /**
   * Enrich a RIChan with future method.
   */
  implicit def enrichRIChanWithFuture[A: Message](richan: channel.RIChan[A]): RIChanWithFuture[A] =
    new RIChanWithFuture(richan)

}

/**
 * "Result", "reply" or "response" output channel interface.
 *
 * As opposed to a generic OChan, this channel interface can output only a single message,
 * which is either success or failure.
 *
 * @tparam A the type of the message returned by the channel
 */
abstract class ROChan[-A] { outer =>

  /**
   * Return a result or a signal on the channel.
   *
   * @param r the result that is either a signal or a value.
   * @return unit
   */
  def done(r: Either[Signal, A]): Unit

  /**
   * Return a value on the channel.
   *
   * @param a the value that must be returned.
   * @return unit
   */
  def success_!(a: A): Unit = done(Right(a))

  /**
   * Return a signal on the channel.
   *
   * @param a the signal that must be returned.
   * @return unit
   */
  def failure_!(signal: Signal): Unit = done(Left(signal))

  /**
   * Create a new channel interface that applies a function to the
   * value returned.
   *
   * Like for stream channels, the thread that will execute the map
   * function is undefined. Therefore, map function is supposed to be
   * "pure".
   *
   * @param f the function applied to the result passed to this channel
   *          in case of success.
   * @return A new channel interface obtained that applies `f` to the result
   * that is passed to it in case of success.
   */
  def map[B](f: B => A): ROChan[B] = new ROChan[B] {
    def done(v: Either[Signal, B]): Unit =
      v.fold(outer.failure_!, b => outer.success_!(f(b)))
  }

  /**
   * A mysterious contravariant flatMap method
   */
  def flatMap[B](f: B => (ROChan[A] => Unit)): ROChan[B] = new ROChan[B] {
    def done(v: Either[Signal, B]) = v match {
      case Right(b) => f(b)(outer)
      case Left(signal) => outer.done(Left(signal))
    }
  }
}

/**
 * Companion object for ROChan
 */
object ROChan {

  private[this] final class ROChanImpl[A](chan: OChan[A]) extends ROChan[A] {

    def done(v: Either[Signal, A]): Unit =
      v match {
        case Right(a) => chan.write(Seg(a), Some(EOS), utils.NOOP)
        case Left(signal) => chan.close(signal)
      }

  }

  /**
   * Convert a standard OChan to a result output channel
   */
  implicit def ochanToROChan[A](ochan: OChan[A]): ROChan[A] = new ROChanImpl(ochan)

  /**
   * Convert a standard OChan to a result output channel
   */
  private[molecule] def apply[A](ochan: OChan[A]): ROChan[A] =
    new ROChanImpl(ochan)

  /**
   * Create a ROChan whose success or error signal is posted to another ROChan.
   *
   * This can be used to post results to a many-to-one buffered channel that
   * aggregates the results of multiple supervised processes.
   *
   * (Note that `sys.OChan` can be implicitly converted to ROChan (see `ochanToROChan`).
   *
   * @param correlator a correlator that will be sent along the result passed to this a channel.
   * @param rochan the return channel on which the success or the error signal will be posted together
   *               with the correlator.
   * @return a ROChan
   *
   */
  def monitoredBy[A: Message, B](correlator: B, rochan: ROChan[(B, Either[Signal, A])]): ROChan[A] =
    new ROChan[A] {
      def done(r: Either[Signal, A]): Unit = try { rochan.success_!((correlator, r)) } catch {
        case t: Throwable =>
      }
    }
}

/**
 * Companion factory object for RChan
 */
object RChan {

  /**
   * Create a new RChan and returns its input and output interfaces.
   *
   * @return input and output interfaces of a new RChan.
   */
  def mk[A: Message](): (RIChan[A], ROChan[A]) = {
    val (i, o) = Chan.mk[A]()
    (RIChan(i), ROChan(o))
  }

}
