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

import java.util.{ concurrent => juc }

/**
 * Trait for non-blocking system-level output channels.
 *
 *  This interface assumes that `write` and `close`
 * methods are invoked sequentially.
 *
 * @tparam A The type of messages.
 */
trait OChan[-A] {

  /**
   * Write a segment on this channel.
   *
   * @param seg the segment to write on the channel
   * @param sigOpt an optional signal indicating if this is the last segment in the stream.
   * @param k the continuation taking the seed on which to write subsequent segments. It may
   *          not be invoked if the termination signal is set.
   *
   * @return Unit
   */
  def write(seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit): Unit

  /**
   * Close this channel.
   *
   * Subsequently to a call to this method, any new segment written to this channel will be poisoned.
   * Contrarily to the poison method on input channels, messages buffered are not poisoned and
   * may still be read by a consumer listening on the other end of this channel.
   *
   * @param signal the termination signal.
   * @return Unit
   */
  def close(signal: Signal): Unit

}

object OChan {

  private[this] val OChanIsPoisonable = new Message[OChan[Nothing]] {
    def poison(message: OChan[Nothing], signal: Signal): Unit = {
      message.close(signal)
    }
  }

  implicit def ochanIsMessage[A]: Message[OChan[A]] = OChanIsPoisonable

  class Nil[A: Message] private[OChan] (val signal: Signal) extends OChan[A] {

    def write(seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit): Unit = {
      seg.poison(signal)
      throw new Error("Cannot write on NilChan:" + signal)
    }

    def close(signal: Signal): Unit = {}

    override def equals(that: Any): Boolean = {
      that.isInstanceOf[Nil[_]] && (this.signal == that.asInstanceOf[Nil[_]].signal);
    }

    override def hashCode = signal.hashCode
  }

  def apply[A: Message](signal: Signal): OChan[A] =
    new Nil(signal)

  def unapply(ochan: OChan[_]): Option[Signal] =
    if (ochan.isInstanceOf[Nil[_]])
      Some(ochan.asInstanceOf[Nil[_]].signal)
    else
      None

  /**
   * Channel that swallows absolutely everything and forever
   */
  def Void[A]: OChan[A] = new OChan[A] {
    def write(seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit): Unit =
      k(this)
    def close(signal: Signal) = {}
  }

}
