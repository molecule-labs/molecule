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
package immutable

abstract class Zipper[A: Message, B: Message] extends IChan[(A, B)] {
  def add[C: Message](t: IChan[(A, B)] => IChan[C]): IChan[C] =
    t(this)
}

object Zipper {

  private def zipS[A, B](as: Seg[A], bs: Seg[B]): (Seg[(A, B)], Option[Either[Seg[A], Seg[B]]]) = {
    var _as = as
    var _bs = bs
    val len = math.min(_as.length, _bs.length)
    val r = new Array[(A, B)](len)
    var i = 0
    while (i < len) {
      val a = _as.head
      _as = _as.tail
      val b = _bs.head
      _bs = _bs.tail
      r(i) = (a, b)
      i += 1
    }
    (Seg.wrap(r), if (_as.isEmpty) { if (_bs.isEmpty) None else Some(Right(_bs)) } else Some(Left(_as)))
  }

  def apply[A: Message, B: Message](ia: IChan[A], ib: IChan[B]): IChan[(A, B)] =
    ia match {
      case IChan(signal) =>
        ib.poison(signal)
        IChan.empty(signal)
      case _ => ib match {
        case IChan(signal) =>
          ia.poison(signal)
          IChan.empty(signal)
        case _ => zip(ia, ib)
      }
    }

  private def zip[A: Message, B: Message](
    ia: IChan[A], ib: IChan[B]): IChan[(A, B)] = new Zipper[A, B] {
    def complexity: Int = math.max(ia.complexity, ib.complexity) + 1

    def read(t: UThread, k: (Seg[(A, B)], IChan[(A, B)]) => Unit): Unit =
      ia.read(t, (as, ia) => zipR(as, ia, ib).read(t, k))

    def poison(signal: Signal) = {
      ia.poison(signal)
      ib.poison(signal)
    }
  }

  private def _zip[A: Message, B: Message](
    ia: IChan[A], ib: IChan[B], rem: Option[Either[Seg[A], Seg[B]]]): IChan[(A, B)] =
    if (rem.isDefined) {
      rem.get match {
        case Left(as) => zipR(as, ia, ib)
        case Right(bs) => zipL(ia, bs, ib)
      }
    } else
      zip(ia, ib)

  private def zipR[A: Message, B: Message](as: Seg[A], ia: IChan[A], ib: IChan[B]): IChan[(A, B)] =
    new Zipper[A, B] {

      def complexity: Int = math.max(ia.complexity, ib.complexity) + 1

      def poison(signal: Signal) = {
        as.poison(signal)
        ia.poison(signal)
        ib.poison(signal)
      }

      def read(t: UThread, k: (Seg[(A, B)], IChan[(A, B)]) => Unit): Unit =
        ib.read(t, {
          case (NilSeg, IChan(signal)) =>
            as.poison(signal)
            ia.poison(signal)
            k(NilSeg, IChan.empty(signal))
          case (NilSeg, ib) =>
            zipR(as, ia, ib).read(t, k)
          case (bs, IChan(signal)) =>
            val (seg, rem) = zipS(as, bs)
            rem match {
              case None =>
                ia.poison(signal)
                k(seg, IChan.empty(signal))
              case Some(Left(as)) =>
                ia.poison(signal)
                as.poison(signal)
                k(seg, IChan.empty(signal))
              case Some(Right(bs)) =>
                ia match {
                  case IChan(signal) =>
                    bs.poison(signal)
                    k(seg, IChan.empty(signal))
                  case _ =>
                    k(seg, zipL(ia, bs, ib))
                }
            }
          case (bs, ib) =>
            val (seg, rem) = zipS(as, bs)
            k(seg, _zip(ia, ib, rem))
        })
    }

  private def zipL[A: Message, B: Message](ia: IChan[A], bs: Seg[B], ib: IChan[B]): IChan[(A, B)] =
    new Zipper[A, B] {

      def complexity: Int = math.max(ia.complexity, ib.complexity) + 1

      def poison(signal: Signal) = {
        bs.poison(signal)
        ia.poison(signal)
        ib.poison(signal)
      }

      def read(t: UThread, k: (Seg[(A, B)], IChan[(A, B)]) => Unit): Unit =
        ia.read(t, {
          case (NilSeg, IChan(signal)) =>
            bs.poison(signal)
            ib.poison(signal)
            k(NilSeg, IChan.empty(signal))
          case (NilSeg, ia) =>
            zipL(ia, bs, ib).read(t, k)
          case (as, IChan(signal)) =>
            val (seg, rem) = zipS(as, bs)
            rem match {
              case None =>
                ib.poison(signal)
                k(seg, IChan.empty(signal))
              case Some(Right(bs)) =>
                ib.poison(signal)
                bs.poison(signal)
                k(seg, IChan.empty(signal))
              case Some(Left(as)) =>
                ib match {
                  case IChan(signal) =>
                    as.poison(signal)
                    k(seg, IChan.empty(signal))
                  case _ =>
                    k(seg, zipR(as, ia, ib))
                }
            }
          case (as, ia) =>
            val (seg, rem) = zipS(as, bs)
            k(seg, _zip(ia, ib, rem))
        })
    }

}
