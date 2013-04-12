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
 * Continue on this channel for size value and then invoke
 * k on the remaining on the chan.
 *
 */
class TakeT[A: Message] private (size: Int, rem: Int, switch: IChan[A] => IChan[A]) extends Transformer[A, A] {

  final def apply(ichan: IChan[A]): IChan[A] = new IChan[A] {

    def complexity = ichan.complexity

    def read(thread: UThread, k: (Seg[A], IChan[A]) => Unit): Unit =
      read_(ichan, thread, k)

    final def read_(src: IChan[A], thread: UThread,
      k: (Seg[A], IChan[A]) => Unit): Unit =
      src.read(thread,
        { (seg, next) =>
          val (a, b) = seg.splitAt(rem)
          val nrem = rem - a.length
          if (nrem == 0) { // We tool everything
            // Order is important! First switch then call k
            val nchan = next match {
              case NilIChan(signal) => switch(b ++: NilIChan(signal))
              case ichan => switch(b ++: FrontIChan(ichan))
            }
            k(a, nchan)
          } else { // b is empty
            next match {
              case NilIChan(signal) =>
                k(a, NilIChan(signal))
              case ichan =>
                k(a, ichan.add(new TakeT(size, nrem, switch)))
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

object TakeT {
  def apply[A: Message](size: Int, k: IChan[A] => IChan[A]): TakeT[A] = {
    require(size >= 0)
    new TakeT(size, size, k)
  }
}