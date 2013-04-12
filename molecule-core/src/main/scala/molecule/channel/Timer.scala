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

import java.util.concurrent.{ TimeUnit, ScheduledExecutorService }
import platform.Platform

/**
 * A timer channel schedules time events on its output using the platform's
 * internal timers facilities. Each time event generated contains the number
 * of ticks generated since the channel was created (2038 bug ready).
 *
 */
object Timer {

  /**
   * Create a Timer Channel that schedule an event after the given
   * delay.
   *
   * @param delay    the time from now to delay execution.
   * @param unit     the time unit of the delay parameter.
   * @return a result channel that returns `unit` when the timer expires.
   * @throws IllegalArgumentException if period less than or equal to zero
   */
  @throws(classOf[IllegalArgumentException])
  def timeout(delay: Long, unit: TimeUnit): RIChan[Unit] = new RIChan[Unit] with Runnable {

    private[this] var DONE: Boolean = false
    private[this] var SIGNAL: Signal = null
    private[this] var K: (Unit => Unit, Signal => Unit) = null

    val sf = Platform.scheduledExecutor.schedule(this, delay, unit)

    def read(success: Unit => Unit, failure: Signal => Unit): Unit = {
      val action: Unit => Unit = synchronized {
        if (DONE)
          success
        else if (SIGNAL != null)
          (u) => failure(SIGNAL)
        else {
          K = (success, failure)
          utils.NOOP
        }
      }
      action(())
    }

    def poison(signal: Signal) = synchronized {
      if (SIGNAL != null) {
        sf.cancel(true)
        if (K != null) {
          val (_, f) = K
          K = null
          f(signal)
        }
      }
    }

    def run() = {
      val action: Unit => Unit = synchronized {
        if (SIGNAL != null)
          utils.NOOP
        else {
          DONE = true
          SIGNAL = EOS
          if (K != null) {
            val (s, _) = K
            K = null
            s
          } else {
            utils.NOOP
          }
        }
      }
      action(())
    }
  }

  /**
   * @see timeout
   */
  def oneShot(delay: Long, unit: TimeUnit): RIChan[Unit] =
    timeout(delay, unit)

  /**
   * Schedule a sequence of delayed messages.
   *
   *  @param spec a sequence of pairs delay/message.
   *  @param unit the time unit
   *  @tparam A the type of the messages
   *  @return A channel that schedule the delivery of each message sequentially according to its delay.
   */
  def scheduled[A: Message](spec: Traversable[(Long, A)], unit: TimeUnit): IChan[A] =
    if (spec.isEmpty) IChan.empty(EOS)
    else {
      val (delay, v) = spec.head
      IChan.bind[A](Timer.schedule(delay, unit)(v), (_, _) => scheduled(spec.tail, unit))
    }

  /**
   * Create a Timer Channel that schedules a task after the given
   * delay.
   *
   * @param delay    the time from now to delay execution.
   * @param unit     the time unit of the delay parameter.
   * @param task     the task to schedule.
   * @return a result channel that produces the result of the task executed after
   * the timeout has elapsed.
   * @throws IllegalArgumentException if period less than or equal to zero
   */
  @throws(classOf[IllegalArgumentException])
  def schedule[A: Message](delay: Long, unit: TimeUnit)(task: => A): RIChan[A] = {

    val (ri, ro) = RChan.mk[A]()
    val sf = Platform.scheduledExecutor.schedule(new Runnable {
      def run = {
        try {
          ro.success_!(task)
        } catch {
          case t: Throwable => ro.failure_!(Signal(t))
        }
      }
    }, delay, unit)

    new RIChan[A] {
      def read(success: A => Unit, failure: Signal => Unit): Unit =
        ri.read(success, failure)
      def poison(signal: Signal) = {
        sf.cancel(true)
        ri.poison(signal)
      }
    }
  }

  /**
   * Create a Timer Channel configured to schedule periodic
   * time events that occur first
   * after the given initial delay, and subsequently with the given
   * period; that is executions will commence after
   * <tt>initialDelay</tt> then <tt>initialDelay+period</tt>, then
   * <tt>initialDelay + 2 * period</tt>, and so on.
   * The generation of periodic events will only terminate via
   * cancellation. Time events not consumed with the period interval
   * are silently dropped.
   *
   * @param initialDelay the time to delay first execution
   * @param period the period between successive executions
   * @param unit the time unit of the initialDelay and period parameters
   * @param capacity the maximum number of time events that can be created without being
   *        consumed. The timer channel closes itself with an error if this capacity is
   *        exceeded.
   * @return a Timer counting the number of ticks generated since
   * the channel was created
   * @see scheduleAtFixedRate
   * @throws IllegalArgumentException if period less than or equal to zero
   */
  @throws(classOf[IllegalArgumentException])
  def apply(initialDelay: Long, delay: Long, unit: TimeUnit, capacity: Int = defaultMaxCapacity): IChan[Int] = {
    val executor = Platform.scheduledExecutor
    new PeriodicTimer(executor, initialDelay, delay, unit, capacity)
  }

  /**
   * Create a Timer Channel configured to schedule periodic
   * time events. The first event occurs immediately.
   *
   * @see apply
   */
  @throws(classOf[IllegalArgumentException])
  def every(period: Long, unit: TimeUnit, capacity: Int = defaultMaxCapacity): IChan[Int] =
    apply(0, period, unit, capacity)

  /**
   * Create a Timer Channel configured to schedule periodic
   * time events. The first event occurs after the specified period.
   *
   * @see apply
   */
  @throws(classOf[IllegalArgumentException])
  def afterAndEvery(period: Long, unit: TimeUnit, capacity: Int = defaultMaxCapacity): IChan[Int] =
    apply(period, period, unit, capacity)

  private[this] val defaultMaxCapacity = 1000

  /**
   * Create a periodic channel.
   */
  def readPeriodically[A: Message](ichan: IChan[A], period: Long, unit: TimeUnit): IChan[A] =
    readPeriodically(ichan, 0, period, unit, Platform.segmentSizeThreshold, defaultMaxCapacity)

  def readPeriodically[A: Message](ichan: IChan[A], period: Long, unit: TimeUnit, maxBatchSize: Int): IChan[A] =
    readPeriodically(ichan, 0, period, unit, maxBatchSize, defaultMaxCapacity)

  def readPeriodically[A: Message](ichan: IChan[A], initDelay: Long, period: Long, unit: TimeUnit): IChan[A] =
    readPeriodically(ichan, initDelay, period, unit, Platform.segmentSizeThreshold, defaultMaxCapacity)

  def readPeriodically[A: Message](ichan: IChan[A], initDelay: Long, period: Long, unit: TimeUnit, maxBatchSize: Int): IChan[A] =
    readPeriodically(ichan, initDelay, period, unit, maxBatchSize, defaultMaxCapacity)

  def readPeriodically[A: Message](ichan: IChan[A], initDelay: Long, period: Long, unit: TimeUnit, maxBatchSize: Int = Platform.segmentSizeThreshold, capacity: Int = defaultMaxCapacity) = {
    val timer = Timer(initDelay, period, unit, capacity)
    _periodic(timer, maxBatchSize, ichan)
  }

  private[this] def _periodic[A: Message](timer: IChan[Int], maxBatchSize: Int, ichan: IChan[A]): IChan[A] = new IChan[A] {

    def read(k: (Seg[A], IChan[A]) => Unit): Unit = {
      timer.read(
        { (_, next) =>
          next match {
            case IChan(signal) => // error with perpetual timer
              ichan.poison(signal)
              k(Seg(), IChan.empty(signal))
            case _ =>
              _read(ichan, k, next)
          }
        }
      )
    }

    final def _read(ichan: IChan[A], k: (Seg[A], IChan[A]) => Unit, timer: IChan[Int]): Unit = {
      ichan.read {
        case (NilSeg, IChan(signal)) =>
          timer.poison(EOS)
          k(NilSeg, IChan.empty(signal))
        case (NilSeg, next) =>
          _read(next, k, timer)
        case (seg, nil @ IChan(signal)) =>
          val (a, b) = seg.splitAt(maxBatchSize)
          if (b.isEmpty)
            k(a, nil)
          else
            k(a, _periodic(timer, maxBatchSize, IChan.cons_!(b, nil)))
        case (seg, next) =>
          val (a, b) = seg.splitAt(maxBatchSize)
          val nnext = if (b.isEmpty) next else IChan.cons_!(b, next)
          k(a, _periodic(timer, maxBatchSize, nnext))
      }
    }

    def poison(signal: Signal): Unit = {
      timer.poison(EOS)
      ichan.poison(signal)
    }
  }

  /**
   * Create a delayed channel.
   */
  def delay[A](ichan: IChan[A], delay: Long, unit: TimeUnit) = {
    val timer = Timer.timeout(delay, unit)
    _delay(timer, ichan)
  }

  private[this] def _delay[A](timer: IChan[Unit], ichan: IChan[A]): IChan[A] =
    new IChan[A] {

      def read(k: (Seg[A], IChan[A]) => Unit): Unit = {
        timer.read { (_, _) =>
          ichan.read(k)
        }
      }

      def poison(signal: Signal): Unit = {
        timer.poison(EOS)
        ichan.poison(signal)
      }
    }

  /**
   * Implementaion.
   */
  private[this] abstract class TimerImpl(period: Long, capacity: Int)
      extends IChan[Int] with Runnable {

    import java.util.concurrent.ScheduledFuture

    private[this] final var K: (Seg[Int], IChan[Int]) => Unit = null
    private[this] final var nextTick: Int = 0
    private[this] final var ticks: Int = 0
    protected[this] var overflow = false

    val sf: ScheduledFuture[_]

    def read(k: (Seg[Int], IChan[Int]) => Unit): Unit = synchronized {

      if (ticks == 0) {
        if (K == null) {
          K = k
        } else {
          throw new ConcurrentReadException
        }
      } else {
        val start = nextTick
        val tickEvents =
          if (ticks == 1) {
            nextTick = start + 1
            Seg(start)
          } else {
            nextTick = nextTick + ticks
            Seg.wrap(Range(start, nextTick))
          }
        ticks = 0
        transfer(tickEvents, k)
      }
    }

    def write(): Unit = synchronized {
      if (K == null) {
        ticks += 1
        if (ticks > capacity) {
          sf.cancel(false)
          overflow = true
        }
      } else {
        val k = K
        K = null
        val start = nextTick
        nextTick += 1
        transfer(Seg(start), k)
      }
    }

    def run() {
      write()
    }

    protected def transfer(ticks: Seg[Int],
      k: (Seg[Int], IChan[Int]) => Unit)

    def poison(signal: Signal) = synchronized {
      sf.cancel(false)
      if (K != null) {
        val k = K
        K = null
        k(Seg(), IChan.empty(signal))
      }
    }
  }

  private class PeriodicTimer(
      executor: ScheduledExecutorService,
      initDelay: Long,
      delay: Long,
      unit: TimeUnit,
      capacity: Int) extends TimerImpl(delay, capacity) {

    val sf = executor.scheduleAtFixedRate(this, initDelay, delay, unit)

    def transfer(ticks: Seg[Int],
      k: (Seg[Int], IChan[Int]) => Unit) {
      if (overflow) {
        sf.cancel(false)
        k(Seg(), IChan.empty(ChannelOverflowSignal(this.getClass.toString, capacity)))
      } else {
        k(ticks, this)
      }
    }
  }

}

object Clock {

  /**
   * Create a Timer Channel configured to schedule periodic
   * time events that occur first
   * after the given initial delay, and subsequently with the given
   * period; that is executions will commence after
   * <tt>initialDelay</tt> then <tt>initialDelay+period</tt>, then
   * <tt>initialDelay + 2 * period</tt>, and so on.
   * The generation of periodic events will only terminate via
   * cancellation. Time events not consumed with the period interval
   * are silently dropped.
   *
   * @param initialDelay the time to delay first execution
   * @param period the period between successive executions
   * @param unit the time unit of the initialDelay and period parameters
   * @return a Timer counting the number of ticks generated since
   * the channel was created
   * @see scheduleAtFixedRate
   * @throws IllegalArgumentException if period less than or equal to zero
   */
  @throws(classOf[IllegalArgumentException])
  def apply(initialDelay: Long, delay: Long, unit: TimeUnit): IChan[Int] =
    Timer(initialDelay, delay, unit, 10)

  def apply(initialDelay: Long, delay: Long, unit: TimeUnit, capacity: Int): IChan[Int] =
    Timer(initialDelay, delay, unit, capacity)

  /**
   * Create a periodic channel.
   */
  def wrap[A: Message](ichan: IChan[A], period: Long, unit: TimeUnit): IChan[A] =
    wrap(ichan, 0, period, unit, 10)

  def wrap[A: Message](ichan: IChan[A], initDelay: Long, period: Long, unit: TimeUnit): IChan[A] =
    wrap(ichan, initDelay, period, unit, 10)

  def wrap[A: Message](ichan: IChan[A], initDelay: Long, period: Long, unit: TimeUnit, maxBatchSize: Int = Platform.segmentSizeThreshold, capacity: Int = 10) =
    Timer.readPeriodically(ichan, initDelay, period, unit, maxBatchSize, capacity)

}