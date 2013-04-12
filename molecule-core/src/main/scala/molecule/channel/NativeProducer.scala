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
 * A NativeProducer interfaces permits to produce messages
 * in a blocking manner from a native thread. This interface is
 * typically used to wrap a cooperative system-level output channel
 * interface into an interface that can be manipulated easily from
 * a native Java thread.
 *
 * It is also common to wrap a Buffer channel of a sufficient large size
 * inside this interface to produce messages asynchronously and in a
 * non-blocking manner from a cooperative process. This works as long as
 * the maximum number buffer size required in order to not block is
 * guaranteed by the application protocol (i.e. request-ack).
 *
 * @tparam A the type of the messages carried by this channel.
 * @see ConsumerInterface to receive messages from an external
 * thread.
 */
abstract class NativeProducer[-A] {

  /**
   * Send a message
   *
   * @param a the message
   *
   * @throws SignalException if the channel is poisoned.
   * The message will be poisoned.
   */
  def send(a: A): Unit = send(Seg(a), None)

  /**
   * Send a segment
   *
   * @param seg the segment
   *
   * @throws SignalException if the channel is poisoned.
   * The message will be poisoned.
   */
  def send(seg: Seg[A]): Unit = send(seg, None)

  /**
   * Send the last message.
   *
   * @param a the message
   * @param signal the signal indicating why the channel is closed
   * @throws SignalException if the channel is poisoned. In this case, the message will be poisoned.
   */
  def send(a: A, signal: Signal): Unit = send(Seg(a), Some(signal))

  /**
   * Send the last segment.
   *
   * @param a the message
   * @param signal the signal indicating why the channel is closed
   * @throws SignalException if the channel is poisoned. In this case, the message will be poisoned.
   */
  def send(seg: Seg[A], signal: Signal): Unit = send(seg, Some(signal))

  /**
   * Send a segment accompanied by an optional termination signal.
   *
   * @param seg the segment
   * @param sigOpt the optional termination signal
   * @throws SignalException if the channel is poisoned.
   * The segment will be poisoned.
   */
  def send(seg: Seg[A], sigOpt: Option[Signal]): Unit

  /**
   * Check if the channel is closed.
   *
   * @return Some signal if the channel is closed
   */
  def isClosed: Option[Signal]

  /**
   * Close the channel.
   *
   * @param signal the signal indicating why the channel is closed
   */
  def close(signal: Signal): Unit

}

object NativeProducer {

  /**
   * Create a NativeProducer interface around a system-level output channel interface.
   *
   * The NativeProducer remains blocked while the continuation of the output channel
   * is suspended.
   *
   * @param ochan the output channel to wrap into a NativeProducer interface
   * @return a NativeProducer channel
   */
  def apply[A](ochan: OChan[A]): NativeProducer[A] = new NativeProducerImpl(ochan)

  /**
   * Create a 1-to-1 buffered channel whose output interface is wrapped into
   * a NativeProducer interface (@see Buffer).
   *
   * @param size the maximum segment size that can be read from the channel.
   *
   * @return the input and output interfaces of the channel
   */
  def mkOneToOne[A: Message](sst: Int): (IChan[A], NativeProducer[A]) = {
    val (i, o) = Buffer.mk(sst)
    (i, new NativeProducerImpl(o))
  }

  /**
   * Create a Many-to-1 channel whose output interface is wrapped into
   * a NativeProducer channel (@see ManyToOne).
   *
   * @param maxSegmentSize If it is greater than 0, the parameter represents the maximum size
   *   of the aggregated segments that can be read from the input side of the channel. If it is equal to 0,
   *   the segments written to the channel will not be aggregated and read one by one.
   *
   * @return the input interfaces of the channel and a factory method for creating an individual
   *         interface for each NativeProducer.
   */
  def mkManyToOne[A: Message](sst: Int): (IChan[A], () => NativeProducer[A]) = {
    val (i, mkO) = ManyToOne.mk(sst)
    (i, () => new NativeProducerImpl(mkO()))
  }

  private final class NativeProducerImpl[A](private[this] final var OCHAN: OChan[A]) extends NativeProducer[A] {

    def send(seg: Seg[A], sigOpt: Option[Signal]): Unit = synchronized {
      if (OCHAN == null)
        throw new ConcurrentWriteException()

      OCHAN match {
        case OChan(signal) => Signal.throwException(signal)
        case ochan =>
          OCHAN = null
          ochan.write(seg, sigOpt, ochan => synchronized { OCHAN = ochan; notify() })
          while (OCHAN == null) wait()
      }
    }

    def isClosed: Option[Signal] = synchronized {
      if (OCHAN == null)
        throw new ConcurrentWriteException()

      OCHAN match {
        case OChan(signal) => Some(signal)
        case _ => None
      }
    }

    def close(signal: Signal): Unit = synchronized {
      if (OCHAN == null)
        throw new ConcurrentWriteException()

      OCHAN.close(signal)
    }

  }
}