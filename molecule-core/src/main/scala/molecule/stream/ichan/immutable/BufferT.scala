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
 * Adds a buffered segment to the head of a ichan
 *
 */
case class BufferT[A: Message](seg: Seg[A]) extends Transformer[A, A] { outer =>

  def apply(signal: Signal): IChan[A] =
    BufferedIChan(seg, signal)

  def apply(ichan: IChan[A]): IChan[A] =
    if (seg.isEmpty)
      ichan
    else
      BufferedIChan(seg, ichan)

}

abstract class BufferedIChan[A] extends StatefulIChan[Seg[A], A] with TestableIChan[A] {

  def test(t: UThread, k: IChan[A] => Unit): Unit =
    k(this)

  def add[B: Message](transformer: Transformer[A, B]): IChan[B] =
    transformer match {
      case buf: BufferT[_] =>
        if (buf.seg.length + state.length > platform.Platform.segmentSizeThreshold)
          transformer(new DelayedIChan(this))
        else
          transformer(this)
      case _ =>
        transformer(this)
    }
}

object BufferedIChan {

  final def apply[A: Message](seg: Seg[A], ichan: IChan[A]): IChan[A] =
    if (seg.isEmpty)
      ichan
    else
      new BufferedIChan[A] {
        def state = seg

        def complexity = ichan.complexity

        def read(t: UThread,
          k: (Seg[A], IChan[A]) => Unit): Unit =
          // In principle we should resubmit to the thread to avoid violating requirement R1
          // but this won't create issues since in practice segments are quite small
          k(seg, ichan)

        def poison(signal: Signal) = {
          state.poison(signal)
          ichan.poison(signal)
        }
      }

  final def apply[A: Message](seg: Seg[A], signal: Signal): BufferedIChan[A] =
    new BufferedIChan[A] {

      def state = seg

      def complexity = 0

      def read(t: UThread,
        k: (Seg[A], IChan[A]) => Unit): Unit =
        k(seg, NilIChan(signal))

      def poison(signal: Signal) =
        state.poison(signal)

    }

}
