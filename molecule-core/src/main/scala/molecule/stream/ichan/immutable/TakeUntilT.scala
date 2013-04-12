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
class TakeUntilT[A: Message, B: Message] private (
    splitter: IChan[B],
    switch: Either[Signal, (B, IChan[B], IChan[A])] => IChan[A]) extends Transformer[A, A] {

  final def apply(ichan: IChan[A]): IChan[A] = new IChan[A] {

    def complexity = ichan.complexity

    def read(thread: UThread, k: (Seg[A], IChan[A]) => Unit): Unit =
      CombineUtils.selectOnce(splitter.testable, ichan.testable)(thread) {
        case Left((NilSeg, IChan(signal), ichan)) => // Splitter terminates
          switch(Left(signal))
          ichan match {
            case nil: NilIChan =>
              k(Seg(), nil)
            case _ =>
              ichan.read(thread, k)
          }
        case Left((seg, splitter, ichan)) =>
          switch(Right((seg.head, seg.tail ++: splitter, ichan)))
          k(Seg(), IChan.empty(EOS))
        case Right((as, ichan, splitter)) =>
          k(as, new TakeUntilT[A, B](splitter, switch).apply(ichan))
      }

    def poison(signal: Signal) = {
      ichan.poison(signal)
      switch(Left(signal))
    }

    def add[B: Message](transformer: Transformer[A, B]) =
      transformer(this)
  }
}

object TakeUntilT {

  def apply[A: Message, B: Message](
    splitter: IChan[B],
    switch: Either[Signal, (B, IChan[B], IChan[A])] => IChan[A]): TakeUntilT[A, B] = {
    TakeUntilT(splitter, switch)
  }
}