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
 * Simple transformations that produce on segement for each segment
 * passing by.
 *
 */
abstract class StatelessT[A, B: Message] extends Transformer[A, B] {
  outer =>

  val complexity: Int

  def transform(seg: Seg[A]): Seg[B]

  def add[C: Message](orig: IChan[A], self: IChan[B], t: Transformer[B, C]): IChan[C]

  def apply(ichan: IChan[A]): IChan[B] = new IChan[B] {

    def complexity = outer.complexity + ichan.complexity

    def read(t: UThread,
      k: (Seg[B], IChan[B]) => Unit) =
      ichan.read(t,
        (seg, next) => k(transform(seg), next.add(outer))
      )

    def poison(signal: Signal) = ichan.poison(signal)

    def add[C: Message](t: Transformer[B, C]): IChan[C] =
      outer.add(ichan, this, t)
  }

}