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

import java.nio.ByteBuffer
import java.nio.channels.{ SelectableChannel => JSelectableChannel, ReadableByteChannel, WritableByteChannel }
import java.net.SocketAddress

import channel.{ IChan, OChan }

/**
 * Factory methods for creating TCP sockets.
 *
 */
object StreamSocket {

  private final class StreamInputChannel(
      _socket: => Socket[ByteBuffer],
      final val niochan: JSelectableChannel with ReadableByteChannel,
      final val selector: IOSelector,
      final val rcvBuf: ByteBuffer) extends InputChannel[ByteBuffer] {

    lazy val socket = _socket

    def doRead(rcvBuf: ByteBuffer): Int = {
      val len = niochan.read(rcvBuf)
      len
    }

    def doCopy(rcvBuf: ByteBuffer): Seg[ByteBuffer] = {
      val buf = new Array[Byte](rcvBuf.remaining)
      rcvBuf.get(buf)
      Seg(ByteBuffer.wrap(buf))
    }

    def closed() = socket.iClosed()
  }

  private final class StreamOutputChannel(
      _socket: => Socket[ByteBuffer],
      final val niochan: JSelectableChannel with WritableByteChannel,
      final val selector: IOSelector,
      final val sndBuf: ByteBuffer) extends OutputChannel[ByteBuffer] {

    lazy val socket = _socket

    @scala.annotation.tailrec
    private final def copy(s: Seg[ByteBuffer], bb: ByteBuffer): Seg[ByteBuffer] = {
      if (s.isEmpty)
        s
      else {
        val buf = s.head
        val array = buf.array
        if (buf.remaining() > bb.remaining) {
          val len = bb.remaining
          bb.put(array, buf.position, len)
          val offset = buf.position + len
          ByteBuffer.wrap(array, offset, buf.limit - offset) +: s.tail
        } else {
          bb.put(array, buf.position, buf.remaining)
          copy(s.tail, bb)
        }
      }
    }

    def doCopy(seg: Seg[ByteBuffer], sndBuf: ByteBuffer): Seg[ByteBuffer] =
      copy(seg, sndBuf)

    def doWrite(sndBuf: ByteBuffer) = {
      niochan.write(sndBuf)
    }

    def closed() = socket.oClosed()
  }

  private[net] def apply(niochan: JSelectableChannel with ReadableByteChannel with WritableByteChannel,
    handle: SocketHandle,
    selector: IOSelector,
    rcvBuf: ByteBuffer,
    sndBuf: ByteBuffer): Socket[ByteBuffer] = {

    lazy val s: Socket[ByteBuffer] = Socket(
      niochan,
      selector,
      handle,
      new StreamInputChannel(s, niochan, selector, rcvBuf),
      new StreamOutputChannel(s, niochan, selector, sndBuf)
    )

    s
  }

}