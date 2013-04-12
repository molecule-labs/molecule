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
package ochan
package immutable

/**
 * Perform stateful operations on a ichan
 *
 */
case class StateT[A, S, B: Message](complexity: Int, state: S, fsm: (S, B) => (S, A))
    extends Transformer[A, B] { outer =>

  final def apply(ochan: OChan[A]): OChan[B] = new OChan[B] {

    def write(t: UThread, seg: Seg[B], sigOpt: Option[Signal], k: OChan[B] => Unit): Unit = {
      val (nextStateT, segb) = seg.smap(state)(fsm)
      ochan.write(t, segb, sigOpt, ochan => k(ochan.add(StateT(complexity, nextStateT, fsm))))
    }

    def close(signal: Signal) =
      ochan.close(signal)

    def add[C: Message](transformer: Transformer[B, C]): OChan[C] = {
      transformer match {
        case StateT(complexity, s, gsm) =>
          StateT(outer.complexity + complexity, (state, s), (ps: (S, Any), c: C) => {
            val (sg, b) = gsm(ps._2, c)
            val (sf, a) = fsm(ps._1, b)
            ((sf, sg), a)
          }).apply(ochan)
        case MapperT(complexity, g) =>
          StateT[A, S, C](outer.complexity + complexity, state, { (s, c) =>
            val (snext, a) = fsm(s, g(c))
            (snext, a)
          }).apply(ochan)
        case _ =>
          transformer(this)
      }
    }
  }
}
