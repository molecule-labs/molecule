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
import java.nio.channels.{ DatagramChannel }
import java.net.{ SocketAddress, DatagramPacket }

import channel.{ IChan, OChan }

/**
 * Factory for DatagramSockets
 *
 */
object DatagramSocket {

  private class DatagramInputChannel(
      _socket: => Socket[DatagramPacket],
      final val niochan: DatagramChannel,
      final val selector: IOSelector,
      final val rcvBuf: ByteBuffer) extends InputChannel[DatagramPacket] {

    final lazy val socket = _socket

    //TODO: SI-3569 final 
    private[this] final var source: SocketAddress = null

    def doRead(rcvBuf: ByteBuffer): Int = {
      try {
        source = niochan.receive(rcvBuf)
      } catch {
        case e: java.nio.channels.ClosedChannelException =>
          return -1
      }
      if (source == null) 0 else rcvBuf.position
    }

    def doCopy(rcvBuf: ByteBuffer): Seg[DatagramPacket] = {
      val length = rcvBuf.remaining
      val buf = new Array[Byte](length)
      rcvBuf.get(buf)
      //println(Utils.showHex(buf))
      Seg(new DatagramPacket(buf, length, source))
    }

    def closed() = socket.iClosed()
  }

  private class DatagramOutputChannel(
      _socket: => Socket[DatagramPacket],
      final val niochan: DatagramChannel,
      final val selector: IOSelector,
      final val sndBuf: ByteBuffer) extends OutputChannel[DatagramPacket] {

    final lazy val socket = _socket

    //TODO: SI-3569 final 
    private[this] final var target: SocketAddress = null

    final def doCopy(seg: Seg[DatagramPacket], sndBuf: ByteBuffer): Seg[DatagramPacket] = {
      val head = seg.head
      sndBuf.put(ByteBuffer.wrap(head.getData, head.getOffset, head.getLength))
      target = head.getSocketAddress
      seg.tail
    }

    def doWrite(sndBuf: ByteBuffer) =
      niochan.send(sndBuf, target)

    def closed() = socket.oClosed()
  }

  def apply(niochan: DatagramChannel,
    handle: SocketHandle,
    selector: IOSelector,
    rcvBuf: ByteBuffer,
    sndBuf: ByteBuffer): Socket[DatagramPacket] = {

    lazy val s: Socket[DatagramPacket] = Socket(
      niochan,
      selector,
      handle,
      new DatagramInputChannel(s, niochan, selector, rcvBuf),
      new DatagramOutputChannel(s, niochan, selector, sndBuf)
    )

    s
  }

  private class ConnectedDatagramInputChannel(
      _socket: => Socket[DatagramPacket],
      final val niochan: DatagramChannel,
      final val selector: IOSelector,
      final val rcvBuf: ByteBuffer) extends InputChannel[DatagramPacket] {

    final lazy val socket = _socket

    def doRead(rcvBuf: ByteBuffer): Int = {
      try {
        niochan.read(rcvBuf)
      } catch {
        case e: java.nio.channels.ClosedChannelException =>
          return -1
      }
    }

    def doCopy(rcvBuf: ByteBuffer): Seg[DatagramPacket] = {
      val length = rcvBuf.remaining
      val buf = new Array[Byte](length)
      rcvBuf.get(buf)
      //println(Utils.showHex(buf))
      Seg(new DatagramPacket(buf, length))
    }

    def closed() = socket.iClosed()
  }

  private class ConnectedDatagramOutputChannel(
      _socket: => Socket[DatagramPacket],
      final val niochan: DatagramChannel,
      final val selector: IOSelector,
      final val sndBuf: ByteBuffer) extends OutputChannel[DatagramPacket] {

    final lazy val socket = _socket

    final def doCopy(seg: Seg[DatagramPacket], sndBuf: ByteBuffer): Seg[DatagramPacket] = {
      val head = seg.head
      sndBuf.put(ByteBuffer.wrap(head.getData, head.getOffset, head.getLength))
      seg.tail
    }

    def doWrite(sndBuf: ByteBuffer) =
      niochan.write(sndBuf)

    def closed() = socket.oClosed()
  }

  def connect(niochan: DatagramChannel,
    handle: SocketHandle,
    selector: IOSelector,
    rcvBuf: ByteBuffer,
    sndBuf: ByteBuffer): Socket[DatagramPacket] = {

    assert(niochan.isConnected)

    lazy val s: Socket[DatagramPacket] = Socket(
      niochan,
      selector,
      handle,
      new ConnectedDatagramInputChannel(s, niochan, selector, rcvBuf),
      new ConnectedDatagramOutputChannel(s, niochan, selector, sndBuf)
    )

    s
  }

}