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

package molecule.net

import SocketOption.DatagramSocketOption

/**
 * Misc UDP related factory methods
 */
object UDP {
  import java.net.{ SocketAddress, DatagramPacket }
  import java.nio.channels.DatagramChannel
  import java.nio.ByteBuffer
  import java.net.InetSocketAddress

  def bind(selector: IOSelector, address: SocketAddress, opts: DatagramSocketOption*): Socket[DatagramPacket] = {
    val niochan = DatagramChannel.open();
    niochan.configureBlocking(false);
    val socket = niochan.socket();
    socket.bind(address);

    opts foreach { _(socket) }

    val rcvBuf = ByteBuffer.allocate(niochan.socket.getReceiveBufferSize)
    val sndBuf = ByteBuffer.allocate(niochan.socket.getSendBufferSize)

    DatagramSocket(niochan, SocketHandle(socket), selector, rcvBuf, sndBuf)
  }

  def bind(selector: IOSelector, local: SocketAddress, remote: SocketAddress, opts: DatagramSocketOption*): Socket[DatagramPacket] = {
    val niochan = DatagramChannel.open();
    niochan.configureBlocking(false);
    val socket = niochan.connect(remote).socket();
    socket.bind(local);

    opts foreach { _(socket) }

    val rcvBuf = ByteBuffer.allocate(niochan.socket.getReceiveBufferSize)
    val sndBuf = ByteBuffer.allocate(niochan.socket.getSendBufferSize)

    DatagramSocket.connect(niochan, SocketHandle(socket), selector, rcvBuf, sndBuf)
  }

}