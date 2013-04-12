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
class DropWhileT[A: Message](val complexity: Int, val p: A => Boolean) extends Transformer[A, A] { outer =>

  final def apply(ichan: IChan[A]): IChan[A] = new IChan[A] {

    def complexity = ichan.complexity + outer.complexity

    def read(t: UThread,
      k: (Seg[A], IChan[A]) => Unit): Unit =
      read_(ichan, t, k)

    final def read_(src: IChan[A],
      t: UThread,
      k: (Seg[A], IChan[A]) => Unit): Unit =
      src.read(t,
        { (seg, next) =>
          val rem = seg.dropWhile(p)
          if (rem.isEmpty) {
            next match {
              case n: NilIChan =>
                k(Seg(), next)
              case ichan =>
                read_(ichan, t, k)
            }
          } else {
            k(rem, next)
          }
        }
      )

    def poison(signal: Signal) =
      ichan.poison(signal)

    def add[B: Message](transformer: Transformer[A, B]) =
      transformer(this)
  }
}

object DropWhileT {

  def apply[A: Message](complexity: Int, p: A => Boolean): DropWhileT[A] =
    new DropWhileT(complexity, p)

  def apply[A: Message](p: A => Boolean): DropWhileT[A] =
    new DropWhileT(1, p)

  def unnapply[A](dw: DropWhileT[A]): Option[A => Boolean] =
    Some(dw.p)
}