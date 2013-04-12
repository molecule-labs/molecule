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
package net

import java.nio.channels.{ SelectableChannel => JSelectableChannel }
import channel.{ IChan, OChan }

/**
 * Base class for Molecule sockets.
 *
 * Molecule Sockets consist in an input stream and an output
 * stream of byte buffers. The socket is automatically closed when
 * both streams are poisoned.
 *
 */
abstract class Socket[T] extends Channel {
  protected[net] val input: InputChannel[T]
  protected[net] val output: OutputChannel[T]

  /**
   * Socket handle.
   *
   * @return a SocketHandle object providing information about the socket.
   */
  def handle: SocketHandle

  /**
   * The socket input stream.
   */
  val ichan: IChan[T]

  /**
   * The socket output stream.
   */
  val ochan: OChan[T]

  /**
   * Close the socket by poisoninig its input and output streams.
   *
   * @param signal the signal with which the socket is poisoned.
   */
  override def shutdown(signal: Signal): Unit = {
    ichan.poison(signal)
    ochan.close(signal)
    super.shutdown()
  }

  private final var _iClosed: Boolean = false
  private final var _oClosed: Boolean = false

  private[net] def oClosed(): Unit = {
    if (!_oClosed) {
      _oClosed = true
      if (_iClosed) {
        niochan.close()
      }
    }
  }

  private[net] def iClosed(): Unit = {
    if (!_iClosed) {
      _iClosed = true
      if (_oClosed) {
        niochan.close()
      }
    }
  }

}

/**
 * Companion object
 *
 * Provide utility methods to assemble Molecule Sockets.
 */
object Socket {

  // We have already Channel is message (should suffice)
  //
  //  private[this] val socketErasureMessage:Message[Socket[Any]] = new Message[Socket[Any]] {
  //    def poison(s:Socket[Any], signal:Signal):Unit = s.shutdown(signal)
  //  }
  //  
  //  implicit def socketIsMessage[A]:Message[Socket[A]] = socketErasureMessage.asInstanceOf[Message[Socket[A]]]

  private[net] def apply[T](niochan: JSelectableChannel, selector: IOSelector, handle: SocketHandle, input: InputChannel[T], output: OutputChannel[T]): Socket[T] = {
    new Impl(niochan, selector, handle, input, output)
  }

  private class Impl[T](
      final val niochan: JSelectableChannel,
      final val selector: IOSelector,
      final val handle: SocketHandle,
      final val input: InputChannel[T],
      final val output: OutputChannel[T]) extends Socket[T] {

    final val ichan = input.init()
    final val ochan = output.init()
  }
}