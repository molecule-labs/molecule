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

import java.util.{ concurrent => juc }

/**
 * Trait for non-blocking system-level input channels.
 *
 * This interface assumes that `read` and `poison`
 * methods are invoked sequentially.
 *
 * @tparam A The type of messages.
 */
trait IChan[+A] {

  /**
   * Read a segment from this channel.
   *
   * @param k the continuation taking the last segment read as parameter
   *          and the seed on which to read subsequent segments.
   * @return Unit
   */
  def read(k: (Seg[A], IChan[A]) => Unit): Unit

  /**
   * Poison this channel and any segment it may have buffered.
   *
   * @param signal the poison signal.
   * @return Unit
   */
  def poison(signal: Signal): Unit

}

object IChan {

  private[this] val IChanIsPoisonable = new Message[IChan[Any]] {
    def poison(message: IChan[Any], signal: Signal): Unit = {
      message.poison(signal)
    }
  }

  implicit def ichanIsMessage[A]: Message[IChan[A]] = IChanIsPoisonable

  case class Nil(val signal: Signal) extends IChan[Nothing] {

    def read(k: (Seg[Nothing], IChan[Nothing]) => Unit): Unit =
      throw new Error("Cannot read on NilChan:" + signal)

    def poison(signal: Signal): Unit = {}

  }

  def unapply(ichan: IChan[_]): Option[Signal] =
    if (ichan.isInstanceOf[Nil])
      Some(ichan.asInstanceOf[Nil].signal)
    else
      None

  private[this] final val eos = new Nil(EOS)

  /**
   * Construct a input channel that terminates immediately with a signal
   *
   *  @param signal the termination signal.
   *  @tparam       A the type of the messages carried by the input channel.
   *  @return An input channel that terminates immediately.
   */
  def empty[A](signal: Signal): IChan[A] = signal match {
    case EOS => eos
    case _ => new Nil(signal)
  }

  /**
   * Construct a input channel that terminates immediately with EOS
   *
   *  @param signal the termination signal.
   *  @tparam       A the type of the messages carried by the input channel.
   *  @return An input channel that terminates immediately.
   */
  def empty[A]: IChan[A] = empty(EOS)

  /**
   * Create an input channel that generates data lazily out of
   * main memory.
   *
   * @param  a    the first message read on the channel.
   * @param  tail the remainder of the channel.
   * @tparam A    the type of the messages carried by the input channel.
   * @return an input channel with a segment prepended.
   *
   */
  def apply[A: Message](a: A, tail: => IChan[A]): IChan[A] =
    IChan.cons(Seg(a), tail)

  /**
   * Create a channel that returns a single segment.
   *
   * @param  seg    the segment read on the channel.
   * @param  signal the termination signal.
   * @tparam A    the type of the messages carried by the input channel.
   * @return an input channel that contains a single segment.
   */
  def apply[A: Message](seg: Seg[A], signal: Signal): IChan[A] =
    IChan.cons_!(seg, IChan.empty(signal))

  /**
   * Create a, input channel that generates data lazily out of
   * main memory.
   *
   * @param  seg the segment to prepend to the channel.
   * @param  tail the original input channel.
   * @tparam A the type of the messages carried by the input channel.
   * @return an input channel with a segment prepended.
   */
  def cons[A: Message](seg: Seg[A], tail: => IChan[A]): IChan[A] = new IChan[A] {

    def read(k: (Seg[A], IChan[A]) => Unit): Unit =
      k(seg, tail)

    def poison(signal: Signal): Unit =
      seg.poison(signal)
    // No need to poison the tail since it has not been evaluated.

  }

  /**
   * Prepend a segment in front of an existing input channel.
   *
   * @param  seg the segment to prepend to the channel.
   * @param  tail the original input channel.
   * @tparam A the type of the messages carried by the input channel.
   * @return an input channel with a segment prepended.
   */
  def cons_![A: Message](seg: Seg[A], tail: IChan[A]): IChan[A] = new IChan[A] {

    def read(k: (Seg[A], IChan[A]) => Unit): Unit =
      k(seg, tail)

    def poison(signal: Signal): Unit = {
      seg.poison(signal)
      tail.poison(signal)
    }
  }

  def bind[A: Message](first: IChan[A], next: (A, Signal) => IChan[A]): IChan[A] = {
    def _bind(last: A, ichan: IChan[A]): IChan[A] = new IChan[A] {
      def read(k: (Seg[A], IChan[A]) => Unit): Unit =
        first.read((seg, ichan) => ichan match {
          case IChan(signal) =>
            if (seg.isEmpty) k(Seg(last), next(last, signal))
            else k(seg, next(seg.last, signal))
          case _ =>
            if (seg.isEmpty) k(Seg(), _bind(last, ichan))
            else k(last +: seg.init, _bind(seg.last, ichan))
        })

      def poison(signal: Signal): Unit = {
        Message.poison(last, signal)
        ichan.poison(signal)
      }
    }

    new IChan[A] {
      def read(k: (Seg[A], IChan[A]) => Unit): Unit =
        first.read((seg, ichan) => ichan match {
          case IChan(signal) =>
            if (seg.isEmpty) k(Seg(), ichan)
            else k(seg, next(seg.last, signal))
          case _ =>
            if (seg.isEmpty) k(Seg(), bind(ichan, next))
            else k(seg.init, _bind(seg.last, ichan))
        })

      def poison(signal: Signal): Unit =
        first.poison(signal)
    }
  }

  @inline
  private[this] final def SST = platform.Platform.segmentSizeThreshold

  /**
   * Create an infinite stream starting at <code>start</code>
   * and incrementing by step <code>step</code>
   *
   * @param start the start value of the stream
   * @param step the increment value of the stream
   * @return the stream starting at value <code>start</code>.
   */
  def from(start: Int, step: Int): IChan[Int] = {
    // note: end limit of range is exclusive
    val end = start + SST * step
    IChan.cons(Seg.wrap(Range(start, end, step)), from(end, step))
  }

  /**
   * Create an infinite stream starting at <code>start</code>
   * and incrementing by 1.
   *
   * @param start the start value of the stream
   * @return the stream starting at value <code>start</code>.
   */
  def from(start: Int): IChan[Int] = {
    val end = start + SST
    IChan.cons(Seg.wrap(Range(start, end)), from(end))
  }

  /**
   * An infinite stream that repeatedly applies a given function to a start value.
   *
   *  @param start the start value of the stream
   *  @param f     the function that's repeatedly applied
   *  @return      the stream returning the infinite sequence of values `start, f(start), f(f(start)), ...`
   */
  def iterate[A: Message](start: A)(f: A => A): IChan[A] = {

    def loop(n: Int, start: A, batch: Seg[A]): (Seg[A], A) = {
      if (n == 0) {
        (batch, start)
      } else {
        val next = f(start)
        loop(n - 1, next, batch :+ next)
      }
    }
    val (seg, next) = loop(SST, start, Seg.empty[A])

    IChan.cons(seg, iterate(next)(f))
  }

  /**
   * Create an infinite stream containing the given element expression (which is computed for each
   * occurrence)
   *
   * @param elem the element composing the resulting stream
   * @return the stream containing an infinite number of elem
   * // TODO add iterator seg
   */
  def continually[A: Message](elem: => A): IChan[A] =
    IChan.cons(Seg.wrap(Vector.fill(SST)(elem)), continually(elem))

  /**
   * Create an infinite stream containing the given element expression
   * which is computed at the time the channel is read. Contrarily
   * to `continually`, this channels emits one message at a time.
   *
   * @param elem the element composing the resulting stream
   * @return the stream containing an infinite number of elem
   */
  def lazily[A](elem: => A): IChan[A] = new IChan[A] {
    def read(k: (Seg[A], IChan[A]) => Unit): Unit =
      k(Seg(elem), this)
    def poison(signal: Signal): Unit = {}
  }

  /**
   * Create an stream of fixed size containing the given element expression
   * which is computed at the time the channel is read
   *
   * @param elem the element composing the resulting stream
   * @return the stream containing an infinite number of elem
   */
  def fill[A: Message](n: Int)(elem: => A): IChan[A] =
    if (n == 0)
      IChan.empty(EOS)
    else if (n <= SST)
      IChan(Seg.wrap(Vector.fill(n)(elem)), EOS)
    else
      IChan.cons(Seg.wrap(Vector.fill(SST)(elem)), fill(n - SST)(elem))

  /**
   * Produces a stream containing values of a given function over a range of integer values starting from 0.
   * @param n The number of elements to generate.
   * @param f The function computing element values
   * @return A new stream consisting of elements `f(0), ..., f(n -1)`
   */
  def tabulate[A: Message](n: Int)(f: Int => A): IChan[A] = {
    if (n == 0)
      IChan.empty(EOS)
    else if (n <= SST)
      IChan(Seg.wrap(Vector.tabulate(n)(f)), EOS)
    else
      IChan.cons(Seg.wrap(Vector.tabulate(SST)(f)), tabulate(n - SST)(f))
  }

  import java.util.concurrent.TimeUnit

  /**
   * Creates a stream with the specified elements.
   * @tparam A the type of the stream's elements
   * @param a the first elements of the created stream
   * @param as the remaining elements of the created stream
   * @return a new stream with the elements passed as parameters
   */
  def apply[A: Message](a: A, as: A*): IChan[A] =
    IChan.source(a +: as.toSeq)

  def apply(r: Range): IChan[Int] =
    IChan.source(r)

  def source[A: Message](as: Traversable[A], sst: Int = SST): IChan[A] = {
    require(sst > 0, "batch size must be greater than 0")
    _source(as, sst min SST)
  }

  private[this] def _source[A: Message](as: Traversable[A], mss: Int): IChan[A] = {
    val (a, b) = as.splitAt(mss)
    if (b.isEmpty)
      IChan(Seg.wrap(a), EOS)
    else
      IChan.cons(Seg.wrap(a), _source(b, mss))
  }

}
