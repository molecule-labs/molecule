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
package channel
package impl

private[channel] object OneToOneBChan {

  def apply[A: Message](size: Int): (IChan[A], OChan[A]) = {
    val ch = new OneToOneBChanImpl(size)
    (ch, ch)
  }

  private[this] final class OneToOneBChanImpl[A: Message](maxBatchSize: Int) extends IChan[A] with OChan[A] {

    private[this] final var SEG: Seg[A] = NilSeg
    private[this] final var WK: OChan[A] => Unit = null
    private[this] final var RK: (Seg[A], IChan[A]) => Unit = null
    private[this] final var SIGNAL: Option[Signal] = None

    import java.util.concurrent.atomic.AtomicInteger
    private[this] final val _lock = new AtomicInteger(0) // 0 = not locked, 1 = locked

    private[this] final def lock(): Unit = {
      // chans, have one producer and one consumer, spinlock is ok
      // since contention is extremely unlikely.
      while (!_lock.compareAndSet(0, 1)) {}
    }

    private[this] final def unlock(): Unit = {
      _lock.set(0)
    }

    def write(seg: Seg[A], sigOpt: Option[Signal], wk: OChan[A] => Unit): Unit = {
      lock()
      if (WK != null) {
        unlock()
        throw new ConcurrentWriteException
      } else if (RK != null) {
        val len = seg.length
        val rk = RK
        RK = null
        SIGNAL = sigOpt
        if ((maxBatchSize > 0) && (len > maxBatchSize)) {
          val (a, b) = seg.splitAt(maxBatchSize)
          SEG = b
          unlock()
          rk(a, this)
        } else {
          unlock()
          transferOnWrite(wk, seg, rk)
        }
      } else if (SIGNAL.isDefined) {
        val signal = SIGNAL.get
        unlock()
        seg.poison(signal)
        wk(OChan(signal))
      } else {
        SEG = SEG ++ seg
        SIGNAL = sigOpt
        if ((maxBatchSize > 0) && (SEG.length > maxBatchSize)) {
          WK = wk
          unlock()
        } else {
          unlock()
          wk(this)
        }
      }
    }

    def close(signal: Signal) = {
      lock()
      _close(signal)
      unlock()
    }

    private[this] final def _close(signal: Signal) = {
      if (SIGNAL.isEmpty) {
        SIGNAL = Some(signal)
        if (RK != null) { // SEG is empty
          val rk = RK
          RK = null
          rk(SEG, IChan.empty(signal))
        }
        if (WK != null) {
          val wk = WK
          WK = null
          wk(OChan(signal))
        }
      } else {
        //System.err.println("WARN:Channel closed twice")
      }
    }

    def read(rk: (Seg[A], IChan[A]) => Unit): Unit = {
      lock()
      if (RK != null) {
        unlock()
        throw new ConcurrentReadException
      } else if (WK != null) {
        val seg = SEG
        val len = seg.length
        if ((maxBatchSize > 0) && (len > maxBatchSize)) {
          val (a, b) = seg.splitAt(maxBatchSize)
          SEG = b
          if (b.length > maxBatchSize) {
            unlock()
            rk(a, this)
          } else {
            val wk = WK
            WK = null
            unlock()
            transferOnRead(wk, a, rk)
          }
        } else {
          val wk = WK
          WK = null
          SEG = Seg.empty
          unlock()
          transferOnRead(wk, seg, rk)
        }
      } else if (SEG.isEmpty) {
        if (SIGNAL.isDefined) {
          unlock()
          rk(SEG, IChan.empty(SIGNAL.get))
        } else {
          RK = rk
          unlock()
        }
      } else {
        val seg = SEG
        val len = seg.length
        if ((maxBatchSize > 0) && (len > maxBatchSize)) {
          val (a, b) = seg.splitAt(maxBatchSize)
          SEG = b
          unlock()
          rk(a, this)
        } else {
          SEG = Seg.empty
          val sig = SIGNAL
          unlock()
          if (sig.isDefined)
            rk(seg, IChan.empty(sig.get))
          else
            rk(seg, this)
        }
      }
    }

    def poison(signal: Signal): Unit = {
      lock()
      SEG.poison(signal)
      SEG = Seg()
      _close(signal)
      unlock()
    }

    private[this] final def transferOnWrite(wk: OChan[A] => Unit,
      seg: Seg[A],
      rk: (Seg[A], IChan[A]) => Unit) {
      if (SIGNAL.isDefined) {
        rk(seg, IChan.empty(SIGNAL.get))
      } else {
        rk(seg, this)
        wk(this)
      }
    }

    private[this] final def transferOnRead(wk: OChan[A] => Unit,
      seg: Seg[A],
      rk: (Seg[A], IChan[A]) => Unit) {
      if (SIGNAL.isDefined) {
        rk(seg, IChan.empty(SIGNAL.get))
      } else {
        wk(this)
        rk(seg, this)
      }
    }
  }
}