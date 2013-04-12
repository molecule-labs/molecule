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

/**
 * Factory for in-memory cooperative (aka rendez-vous) channels
 */
object Chan {

  /**
   * Create a in-memory cooperative channel.
   *
   * @tparam A the type of the messages accepted by this channel.
   * @return the input and output interfaces of the channel.
   */
  def mk[A: Message](): (IChan[A], OChan[A]) = {
    val ch = new Chan[A]()
    (ch, ch)
  }

  import utils.Unsafe.{ instance => unsafe }

  private[this] final class Chan[A: Message] extends IChan[A] with OChan[A] {

    private[this] final var SEG: Seg[A] = NilSeg
    private[this] final var SIGNAL: Signal = null
    private[this] final var WK: OChan[A] => Unit = null
    private[this] final var RK: (Seg[A], IChan[A]) => Unit = null

    // This channel can have only one producer and one consumer. 
    // Therefore, using a spin-lock is fine here since contention is extremely unlikely.

    @volatile private[this] var LOCK: Int = 0 // 0 = not locked, 1 = locked

    import Chan._

    @scala.annotation.tailrec
    private[this] final def lock(): Unit = {
      if (!unsafe.compareAndSwapInt(this, lockOffset, 0, 1)) lock()
    }

    private[this] final def unlock(): Unit = {
      LOCK = 0
    }

    def write(seg: Seg[A], sigOpt: Option[Signal], wk: OChan[A] => Unit): Unit = {
      lock()
      if (WK ne null) {
        unlock()
        throw new ConcurrentWriteException
      } else if (RK ne null) {
        val rk = RK
        RK = null
        SIGNAL = sigOpt.getOrElse(null)
        unlock()
        transferOnWrite(wk, seg, rk)
      } else if (SIGNAL ne null) {
        unlock()
        val signal = SIGNAL
        seg.poison(signal)
        wk(OChan(signal))
      } else {
        SEG = seg
        SIGNAL = sigOpt.getOrElse(null)
        WK = wk
        unlock()
      }
    }

    def close(signal: Signal) = {
      lock()
      _close(signal)
      unlock()
    }

    private[this] final def _close(signal: Signal) = {
      if (SIGNAL eq null) {
        SIGNAL = signal
        if (RK ne null) { // SEG is empty
          val rk = RK
          RK = null
          rk(SEG, IChan.empty(signal))
        }
        if (WK ne null) {
          val wk = WK
          WK = utils.NOOP
          wk(OChan(signal))
        }
      } else {
        //System.err.println("WARN:Channel closed twice")
      }
    }

    def read(rk: (Seg[A], IChan[A]) => Unit): Unit = {
      lock()
      if (RK ne null) {
        unlock()
        throw new ConcurrentReadException
      } else if (WK ne null) {
        val wk = WK
        WK = null
        val seg = SEG
        SEG = Seg.empty
        unlock()
        transferOnRead(wk, seg, rk)
      } else if (SIGNAL ne null) {
        val seg = SEG
        SEG = Seg.empty
        unlock()
        rk(seg, IChan.empty(SIGNAL))
      } else {
        RK = rk
        unlock()
      }
    }

    def poison(signal: Signal): Unit = {
      lock()
      SEG.poison(signal)
      SEG = Seg.empty
      _close(signal)
      unlock()
    }

    private[this] final def transferOnWrite(wk: OChan[A] => Unit,
      seg: Seg[A],
      rk: (Seg[A], IChan[A]) => Unit) {
      if (SIGNAL ne null) {
        rk(seg, IChan.empty(SIGNAL))
      } else {
        rk(seg, this)
        wk(this)
      }
    }

    private[this] final def transferOnRead(wk: OChan[A] => Unit,
      seg: Seg[A],
      rk: (Seg[A], IChan[A]) => Unit) {
      if (SIGNAL ne null) {
        rk(seg, IChan.empty(SIGNAL))
      } else {
        wk(this)
        rk(seg, this)
      }
    }

  }

  private[this] object Chan {
    private val lockOffset = unsafe.objectFieldOffset(classOf[Chan[_]].getDeclaredField("LOCK"))
  }

}