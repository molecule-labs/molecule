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
 * Filter out values that do not satisfy a given predicate
 *
 */
case class FilterT[A: Message](val complexity: Int, p: A => Boolean) extends Transformer[A, A] {
  outer =>

  def apply(ichan: IChan[A]) = new IChan[A] {

    def complexity = outer.complexity + ichan.complexity

    def read(t: UThread,
      k: (Seg[A], IChan[A]) => Unit) =
      _read(ichan, t, k)

    final def _read(
      ch: IChan[A],
      t: UThread,
      k: (Seg[A], IChan[A]) => Unit): Unit =
      ch.read(t,
        (seg, next) => {
          val sf = seg.filter(p)
          if (sf.isEmpty) {
            next match {
              case n: NilIChan => k(sf, n)
              case _ => _read(next, t, k)
            }
          } else
            k(sf, next.add(outer))
        }
      )

    def poison(signal: Signal) = ichan.poison(signal)

    def add[B: Message](t: Transformer[A, B]): IChan[B] =
      outer.add(ichan, this, t)
  }

  def add[C: Message](orig: IChan[A], self: IChan[A], t: Transformer[A, C]): IChan[C] =
    t match {
      case f: FilterT[_] => // f:FilterT[A] => unchecked warning
        val filter = f.asInstanceOf[FilterT[A]]
        _add(filter)(orig).asInstanceOf[IChan[C]]
      case _ =>
        t(self)
    }

  private[this] def _add(filter: FilterT[A]): FilterT[A] =
    new FilterT[A](filter.complexity + complexity,
      a => filter.p(a) && p(a))
  // TODO: Main bottleneck in low-level prime sieve:
  // This closures invokes these two functions systematically:
  // 2 timers scala.runtime.BoxesRunTime.unboxToBoolean(Object) for
  // 1 scala.runtime.BoxesRunTime.boxToBoolean(boolean)
}