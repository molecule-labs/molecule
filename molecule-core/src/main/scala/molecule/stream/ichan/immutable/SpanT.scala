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
 * Create a channel that produces messages as long
 * as the predicate is true and then invoke
 * k on the remaining on the chan.
 *
 */
case class SpanT[A: Message](complexity: Int, p: A => Boolean, switch: IChan[A] => IChan[A]) extends Transformer[A, A] { outer =>

  final def apply(ichan: IChan[A]): IChan[A] = new IChan[A] {

    def complexity = ichan.complexity + outer.complexity

    def read(t: UThread, k: (Seg[A], IChan[A]) => Unit): Unit =
      _read(ichan, t, k)

    private[this] final def _read(ichan: IChan[A], t: UThread, k: (Seg[A], IChan[A]) => Unit): Unit =
      ichan.read(t,
        { (seg, ichan) =>
          val (a, b) = seg.span(p)

          if (b.isEmpty && !ichan.isInstanceOf[NilIChan]) { // span more
            //(a :: ichan.add(outer)).read(t, k)
            // Optimisation:
            if (a.isEmpty)
              _read(ichan, t, k)
            else
              k(a, ichan.add(outer))

          } else {
            val nchan = switch(b ++: FrontIChan(ichan))

            if (nchan.isInstanceOf[NilIChan]) {
              k(a, nchan)
            } else {
              // following would fail if (nchan == NilIChan && a.isEmpty)
              (a ++: nchan).read(t, k)
            }
          }
        }
      )

    def poison(signal: Signal) = {
      ichan.poison(signal)
      switch(NilIChan(signal))
    }

    def add[B: Message](transformer: Transformer[A, B]) =
      transformer(this)
  }
}