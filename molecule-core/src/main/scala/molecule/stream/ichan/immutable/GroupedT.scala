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

/**
 * Same as List.grouped
 */
case class GroupedT[A: Message] private (size: Int, acc: Seg[A]) extends Transformer[A, Seg[A]] {

  final def apply(ichan: IChan[A]): IChan[Seg[A]] = new IChan[Seg[A]] {

    def complexity = ichan.complexity

    def read(thread: UThread,
      k: (Seg[Seg[A]], IChan[Seg[A]]) => Unit): Unit = {
      _read(ichan, acc, thread, k)
    }

    @scala.annotation.tailrec
    private[this] final def groups(result: Seg[Seg[A]], seg: Seg[A]): (Seg[Seg[A]], Seg[A]) =
      if (seg.length < size)
        (result, seg)
      else {
        val (a, b) = seg.splitAt(size)
        groups(result :+ a, b)
      }

    private[this] final def _read(
      src: IChan[A], acc: Seg[A],
      t: UThread,
      k: (Seg[Seg[A]], IChan[Seg[A]]) => Unit): Unit =
      src.read(t,
        { (seg, next) =>
          val rem = size - acc.length
          if (seg.length >= rem) {
            val (a, b) = seg.splitAt(rem)

            val (result, nacc) = groups(Seg(acc ++ a), b)
            next match {
              case NilIChan(signal) =>
                k(result :+ nacc, NilIChan(signal))
              case ichan =>
                k(result, ichan.add(new GroupedT(size, nacc)))
            }
          } else {
            next match {
              case NilIChan(signal) =>
                k(Seg(acc ++ seg), NilIChan(signal))
              case ichan =>
                _read(ichan, acc ++ seg, t, k)
            }
          }
        }
      )

    def poison(signal: Signal) = {
      acc.poison(signal)
      ichan.poison(signal)
    }

    def add[B: Message](transformer: Transformer[Seg[A], B]) =
      transformer(this)

  }
}

object GroupedT {
  def apply[A: Message](size: Int): GroupedT[A] = {
    require(size >= 1)
    new GroupedT(size, Seg.empty)
  }
}
