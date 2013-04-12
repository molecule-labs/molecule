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
 * Perform stateful operations on every element of an ichan
 *
 */
case class StateT[A, S, B: Message](complexity: Int, state: S, fsm: (S, A) => (S, B))
    extends Transformer[A, B] { outer =>

  final def apply(ichan: IChan[A]): IChan[B] = new IChan[B] {

    def complexity = ichan.complexity + outer.complexity

    def read(t: UThread, k: (Seg[B], IChan[B]) => Unit): Unit =
      ichan.read(t,
        { (seg, next) =>
          val (nextStateT, segb) = seg.smap(state)(fsm)
          next match {
            case NilIChan(signal) =>
              k(segb, NilIChan(signal))
            case ichan =>
              k(segb, ichan.add(StateT(outer.complexity, nextStateT, fsm)))
          }
        }
      )

    def poison(signal: Signal) =
      ichan.poison(signal)

    def add[C: Message](transformer: Transformer[B, C]): IChan[C] = {
      transformer match {
        case StateT(complexity, s, gsm) =>
          StateT(outer.complexity + complexity, (state, s), (ps: (S, Any), a: A) => {
            val (sf, b) = fsm(ps._1, a)
            val (sg, c) = gsm(ps._2, b)
            ((sf, sg), c)
          }).apply(ichan)
        case MapperT(complexity, g) =>
          StateT[A, S, C](outer.complexity + complexity, state, { (s, a) =>
            val (snext, b) = fsm(s, a)
            (snext, g(b))
          }).apply(ichan)
        case _ =>
          transformer(this)
      }
    }
  }
}