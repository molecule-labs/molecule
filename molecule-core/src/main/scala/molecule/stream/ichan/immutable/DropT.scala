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
 * Drop elements from the beginning of a channel while they satisfy
 * a predicate
 *
 */
class DropT[A: Message](val complexity: Int, val count: Int) extends Transformer[A, A] { outer =>

  final def apply(ichan: IChan[A]): IChan[A] = new IChan[A] {

    def complexity = ichan.complexity + outer.complexity

    def read(t: UThread,
      k: (Seg[A], IChan[A]) => Unit): Unit =
      read_(ichan, count, t, k)

    final def read_(src: IChan[A],
      count: Int,
      t: UThread,
      k: (Seg[A], IChan[A]) => Unit): Unit =
      src.read(t,
        { (seg, next) =>
          val l = seg.length
          if (l >= count) {
            val rem = seg.drop(count)
            k(rem, next)
          } else {
            next match {
              case nil: NilIChan =>
                k(Seg(), nil)
              case ichan =>
                read_(ichan, count - l, t, k)
            }
          }
        }
      )

    def poison(signal: Signal) =
      ichan.poison(signal)

    def add[B: Message](transformer: Transformer[A, B]) =
      transformer(this)
  }
}

object DropT {

  def apply[A: Message](complexity: Int, count: Int): DropT[A] =
    new DropT(complexity, count)

  def apply[A: Message](count: Int): DropT[A] =
    new DropT(1, count)

  def unnapply[A](d: DropT[A]): Option[Int] =
    Some(d.count)
}