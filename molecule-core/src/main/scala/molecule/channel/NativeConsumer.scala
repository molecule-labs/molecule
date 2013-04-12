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
 * A NativeConsumer interface permits to consume messages in a blocking manner
 * from a native thread. This interface is typically used to wrap a
 * system-level input channel interface into an interface that can be manipulated
 * easily from a native Java thread.
 *
 * @tparam A the type of the messages transported by this channel.
 */
trait NativeConsumer[+A] {

  /**
   * Take a segment from the interface or block if there is none.
   *
   * @throws SignalException if the channel is poisoned.
   */
  def takeSeg(): Seg[A]

  /**
   * Poison the channel
   */
  def poison(signal: Signal): Unit

}

object NativeConsumer {

  /**
   * Create a NativeConsumer interface around a system-level input channel interface.
   *
   * The NativeConsumer remains blocked as long as no new segment is available.
   *
   *  @return the input and output interfaces of the channel
   */
  def apply[A](ichan: IChan[A]): NativeConsumer[A] = null

  private[this] final class NativeConsumerImpl[A](private[this] final var ICHAN: IChan[A]) extends NativeConsumer[A] {

    final def takeSeg(): Seg[A] = {
      var SEG = Seg.empty[A]
      synchronized {
        if (ICHAN == null)
          throw new ConcurrentReadException()

        ICHAN match {
          case IChan(signal) => throw Signal.throwException(signal)
          case ichan =>
            ICHAN = null
            ICHAN.read((seg, ichan) => synchronized { SEG = seg; ICHAN = ichan; notify() })
            while (ICHAN == null)
              wait()
        }
      }
      if (SEG.isEmpty) takeSeg()
      else SEG
    }

    def poison(signal: Signal): Unit = synchronized {
      if (ICHAN == null)
        throw new ConcurrentReadException()

      ICHAN.poison(signal)
    }
  }
}