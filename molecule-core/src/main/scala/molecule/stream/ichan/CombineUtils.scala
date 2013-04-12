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
package stream
package ichan

object CombineUtils {

  private[this] class Append[A](first: IChan[A], second: PartialFunction[Signal, IChan[A]]) extends IChan[A] {

    def complexity = first.complexity

    def read(t: UThread, k: (Seg[A], IChan[A]) => Unit): Unit = {
      first.read(t, {
        case (seg, nil @ IChan(signal)) =>
          if (second.isDefinedAt(signal))
            k(seg, second(signal))
          else k(NilSeg, nil)
        case (seg, first) =>
          k(seg, new Append(first, second))
      })
    }

    def add[B: Message](t: IChan[A] => IChan[B]): IChan[B] =
      t(this)

    def poison(signal: Signal): Unit =
      first.poison(signal)
  }

  def append[A](first: IChan[A], second: PartialFunction[Signal, IChan[A]]): IChan[A] =
    first match {
      case IChan(signal) =>
        if (second.isDefinedAt(signal))
          second(signal)
        else
          first
      case _ =>
        new Append(first, second)
    }

  ////////////////////////////////////////////////////////////////////////////////////////////////////
  // TODO: Probably useful in other places
  private[this] def safeRead[A](ichan: IChan[A])(t: UThread, k: (Seg[A], IChan[A]) => Unit): Unit = {
    if (ichan.isInstanceOf[NilIChan])
      k(NilSeg, ichan)
    else
      ichan.read(t, k)
  }

  final def selectOnce[A: Message, B: Message, C](tia: TestableIChan[A], tib: TestableIChan[B])(t: UThread)(f: Either[(Seg[A], IChan[A], IChan[B]), (Seg[B], IChan[B], IChan[A])] => Unit): Unit = {

    val r = utils.random.nextInt(2)
    var pending = true

    if (r == 0) {
      tia.test(t, ia => if (pending) {
        pending = false
        safeRead(ia)(t, (seg, nia) =>
          f(Left((seg, nia, tib)))
        )
      })
      if (pending) { // this is tricky because first test might be synchronous if data is buffered on tia
        tib.test(t, ib => if (pending) {
          pending = false
          safeRead(ib)(t, (seg, nib) =>
            f(Right((seg, nib, tia)))
          )
        })
      }
    } else {
      tib.test(t, ib => if (pending) {
        pending = false
        safeRead(ib)(t, (seg, nib) =>
          f(Right((seg, nib, tia)))
        )
      })
      if (pending) {
        tia.test(t, ia => if (pending) {
          pending = false
          safeRead(ia)(t, (seg, nia) =>
            f(Left((seg, nia, tib)))
          )
        })
      }
    }
  }

  /**
   * This one is useful for checking timeout and ensure that a timeout channel is only created
   * if there is no data on the left channel. The boolean flag is true is the second channel has
   * not been evaluated.
   *
   * Note that `f` will be called immediately if the next segment is empty and/or the next channel is Nil.
   */
  final def leftBiasedSelectOnce[A: Message, B: Message, C](tia: TestableIChan[A], tib: => TestableIChan[B])(t: UThread)(f: Either[(Boolean, Seg[A], IChan[A], IChan[B]), (Seg[B], IChan[B], IChan[A])] => Unit): Unit = {

    var pending = true
    var immediate = true
    tia.test(t, ia => if (pending) {
      pending = false
      safeRead(ia)(t, (seg, nia) =>
        f(Left((immediate, seg, nia, tib)))
      )
    })
    t.submit(
      if (pending) { // this is tricky because first test might be synchronous if data is buffered on tia
        immediate = false
        tib.test(t, ib => if (pending) {
          pending = false
          safeRead(ib)(t, (seg, nib) =>
            f(Right((seg, nib, tia)))
          )
        })
      }
    )
  }

  private[this] case class SelectIChan[A, B, C](tia: TestableIChan[A], tib: TestableIChan[B])(f: Either[(Seg[A], IChan[A], IChan[B]), (Seg[B], IChan[B], IChan[A])] => IChan[C]) extends IChan[C] {

    lazy val complexity: Int = tia.complexity + tib.complexity

    def read(t: UThread, k: (Seg[C], IChan[C]) => Unit): Unit = {

      val r = utils.random.nextInt(2)
      var pending = true

      if (r == 0) {
        tia.test(t, ia => if (pending) {
          pending = false
          safeRead(ia)(t, (seg, nia) =>
            f(Left((seg, nia, tib))).read(t, k)
          )
        })
        if (pending) { // this is tricky because first test might be synchronous if data is buffered on tia
          tib.test(t, ib => if (pending) {
            pending = false
            safeRead(ib)(t, (seg, nib) =>
              f(Right((seg, nib, tia))).read(t, k)
            )
          })
        }
      } else {
        tib.test(t, ib => if (pending) {
          pending = false
          safeRead(ib)(t, (seg, nib) =>
            f(Right((seg, nib, tia))).read(t, k)
          )
        })
        if (pending) {
          tia.test(t, ia => if (pending) {
            pending = false
            safeRead(ia)(t, (seg, nia) =>
              f(Left((seg, nia, tib))).read(t, k)
            )
          })
        }
      }
    }

    def poison(signal: Signal): Unit = {
      tia.poison(signal)
      tib.poison(signal)
    }

    def add[D: Message](transformer: IChan[C] => IChan[D]): IChan[D] =
      transformer(this)
  }

  private[stream] final def select[A: Message, B: Message, C](ia: IChan[A], ib: IChan[B])(f: Either[(Seg[A], IChan[A], IChan[B]), (Seg[B], IChan[B], IChan[A])] => IChan[C]): IChan[C] =
    new SelectIChan(ia.testable, ib.testable)(f)

  /**
   * Interleave the messages produced asynchronously on a channel with the messages
   * produced on an other channel.
   * This builds a new input channel that produces one message of type Either[A, B] for every
   * message produce on one or the other channel, where `A` is the type of messages produced
   * on the `left` channel and `B` is the type of messages
   * produced on the `right` channel. By default, the resulting channel is closed when both
   * input channels are closed.
   *
   * @param left the left input channel
   * @param right the right input channel
   * @param closeIfLeftClosed flag that indicating if the resulting channel must be closed if this
   *                          channel is closed.
   * @param closeIfRightClosed flag that indicating if the resulting channel must be closed if the other
   *                          channel is closed.
   * @return a new input channel that produces one message of type Either[A, B] for every
   * message produce on this or the other channel
   */
  final def interleave[A: Message, B: Message](left: IChan[A], right: IChan[B], closeIfLeftClosed: Boolean = false, closeIfRightClosed: Boolean = false): IChan[Either[A, B]] = {
    left match {
      case IChan(signal) =>
        if (closeIfLeftClosed)
          return IChan.empty(signal)
        else
          return right.map[Either[A, B]](Right(_))
      case _ =>
    }
    right match {
      case IChan(signal) =>
        if (closeIfRightClosed)
          return IChan.empty(signal)
        else
          return left.map[Either[A, B]](Left(_))
      case _ =>
    }

    select(left, right) {
      case Left((NilSeg, IChan(signal), IChan(_))) =>
        IChan.empty(signal)
      case Left((as, ia, ib)) =>
        as.map(Left(_)) ++: interleave(ia, ib, closeIfLeftClosed, closeIfRightClosed)
      case Right((bs, ib, ia)) =>
        bs.map(Right(_)) ++: interleave(ia, ib, closeIfLeftClosed, closeIfRightClosed)
    }
  }

  private[this] case class MergeIChan[A](sel: SelectIChan[A, A, A]) extends IChan[A] {
    def complexity = sel.complexity

    def read(t: UThread, k: (Seg[A], IChan[A]) => Unit): Unit =
      sel.read(t, k)

    def poison(signal: Signal): Unit =
      sel.poison(signal)

    def add[D: Message](transformer: IChan[A] => IChan[D]): IChan[D] =
      transformer(this)

  }

  final def merge[A: Message](left: IChan[A], right: IChan[A]): IChan[A] = {

    left match {
      case n: NilIChan =>
        return right
      case _ =>
    }
    right match {
      case n: NilIChan =>
        return left
      case _ =>
    }

    select(left, right) {
      case Left((NilSeg, IChan(signal), IChan(_))) =>
        IChan.empty(signal)
      case Left((as, ia, ib)) =>
        as ++: merge(ia, ib)
      case Right((bs, ib, ia)) =>
        bs ++: merge(ib, ia)
    }

  }

  //////////////////////////////////////////////////////////////////////////////////////////

  private final class ZipChan[A: Message, B: Message](
      ia: IChan[A],
      ib: IChan[B],
      f: (Seg[A], IChan[A], Seg[B], IChan[B]) => (Seg[(A, B)], IChan[(A, B)])) extends IChan[(A, B)] {

    lazy val complexity: Int = ia.complexity + ib.complexity

    def read(t: UThread, k: (Seg[(A, B)], IChan[(A, B)]) => Unit): Unit = {
      ia.read(t, (as, ia) => ib.read(t, (bs, ib) => { val (s, c) = f(as, ia, bs, ib); k(s, c) }))
    }

    def poison(signal: Signal): Unit = {
      ia.poison(signal); ib.poison(signal)
    }

    def add[C: Message](transformer: IChan[(A, B)] => IChan[C]): IChan[C] =
      transformer(this)

  }

  object ZipChan {

    private def zipSegs[A: Message, B: Message](as: Seg[A], ia: IChan[A], bs: Seg[B], ib: IChan[B]): (Seg[(A, B)], IChan[(A, B)]) = {
      Seg.zipAsMuch(as, bs) match {
        case (seg, None) => (seg, ZipChan(ia, ib))
        case (seg, Some(Left(as))) => (seg, ZipChan(as ++: ia, ib))
        case (seg, Some(Right(bs))) => (seg, ZipChan(ia, bs ++: ib))
      }
    }

    def apply[A: Message, B: Message](ia: IChan[A], ib: IChan[B]): IChan[(A, B)] = {
      ia match {
        case IChan(signal) =>
          ib.poison(signal)
          return IChan.empty(signal)
        case _ =>
          ib match {
            case IChan(signal) =>
              ia.poison(signal)
              return IChan.empty(signal)
            case _ =>
          }
      }
      new ZipChan(ia, ib, (as: Seg[A], ia: IChan[A], bs: Seg[B], ib: IChan[B]) => zipSegs(as, ia, bs, ib))
    }
  }
}