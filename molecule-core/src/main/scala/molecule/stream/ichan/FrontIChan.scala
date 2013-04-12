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

/**
 * A front channel has two roles:
 * - monitors the complexity of channel
 * transformations and schedules the new channel operations
 * on a thread pool if the complexity becomes too high.
 * - Ensure that segments never exceed the max segment size
 * configured in the platform
 *
 * We can do this because all channel operations are immutable.
 *
 */
final class FrontIChan[A: Message] private[ichan] (
    private[molecule] final val ichan: IChan[A]) extends IChan[A] {

  def complexity = ichan.complexity

  def read(thread: UThread,
    k: (Seg[A], IChan[A]) => Unit): Unit = {
    ichan.read(thread,
      { (seg, next) =>
        val mss = platform.Platform.segmentSizeThreshold
        if (seg.length <= mss) {
          k(seg, FrontIChan.reapply(next))
        } else {
          val (a, b) = seg.splitAt(mss)
          val nnext = new DelayedIChan(new FrontIChan(b ++: next))
          k(a, nnext)
        }
      }
    )
  }

  def poison(signal: Signal): Unit =
    ichan.poison(signal)

  def add[B: Message](transformer: Transformer[A, B]): IChan[B] =
    if (transformer.isInstanceOf[immutable.BufferT[_]]) // optimization (TODO: Measure effectiveness)
      FrontIChan.reapply(transformer(ichan))
    else {
      val nchan = ichan.add(transformer)
      //println(ichan.complexity + "," + nchan.complexity)
      if (nchan.complexity > platform.Platform.complexityCutoffThreshold)
        FrontIChan.cutoff(new FrontIChan(nchan))
      else
        FrontIChan.reapply(nchan)
    }
}

object FrontIChan {

  /* Create a channel on behalf of a component because the
   * complexity of a stream becomes too high.
   */
  def apply[A: Message](ichan: IChan[A]): IChan[A] =
    reapply(ichan)

  // This is called a lot.	
  private final def reapply[A: Message](ichan: IChan[A]): IChan[A] =
    if (ichan.isInstanceOf[NilIChan])
      ichan
    else
      new FrontIChan(ichan)

  private def cutoff[A: Message](ichan: IChan[A]): IChan[A] = {
    new Cutoff(ichan)
  }

  private[this] class Cutoff[A: Message](ichan: IChan[A]) extends IChan[A] {

    def complexity = 1

    def read(t: UThread,
      k: (Seg[A], IChan[A]) => Unit): Unit = {
      val (i, o) = channel.Chan.mk[A]()
      val fcochan = ochan.BackOChan(o)
      t.platform.launch(ichan.connect(fcochan))
      val fcichan = FrontIChan(BackIChan(i))
      fcichan.read(t, k)
    }

    def poison(signal: Signal): Unit =
      ichan.poison(signal)

    def add[B: Message](transformer: Transformer[A, B]): IChan[B] =
      transformer(this)
  }

}
