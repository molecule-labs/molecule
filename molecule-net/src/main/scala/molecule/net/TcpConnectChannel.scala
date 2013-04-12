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

import java.nio.channels.{ SelectionKey, SocketChannel, ServerSocketChannel }
import java.nio.ByteBuffer
import java.net.InetSocketAddress

import channel.{ RChan, RIChan, ROChan }

/**
 * A channel that returns a client socket.
 *
 */
private[net] abstract class TcpConnectChannel extends Channel {

  /**
   * Method invoked asynchronously by the IO thread once
   * the socket is ready
   */
  private[net] def connectReady()

}

/**
 * Factory methods for client sockets
 *
 */
object TcpConnectChannel {

  /**
   * Create a TCP connection to a remote server.
   *
   * @param ioselector the selector in charge of the client socket.
   * @param config the client configuration.
   *
   * @return a tuple with the local address the client is bound to and
   *         a ResultChannel returning the client socket once it is connected.
   */
  def apply(ioselector: IOSelector, config: TcpClientConfig): RIChan[Socket[ByteBuffer]] = {
    val niochan = SocketChannel.open();
    niochan.configureBlocking(false);

    val socket = niochan.socket
    if (config.bindAddress.isDefined)
      socket.bind(config.bindAddress.get)

    config(socket)

    niochan.connect(config.remoteAddress)

    val (ri, ro) = RChan.mk[Socket[ByteBuffer]]()
    new TcpConnectChannelImpl(ioselector, niochan, config, ro)
    ri
  }

  private final class TcpConnectChannelImpl(ioselector: IOSelector, val niochan: SocketChannel, private[this] final var config: TcpClientConfig, private[this] final var result: ROChan[Socket[ByteBuffer]]) extends TcpConnectChannel { self =>

    ioselector.schedule(new Runnable { def run = ioselector.registerConnect(self) })

    private[net] def connectReady() {

      try {
        niochan.finishConnect()
      } catch {
        case ex: java.io.IOException =>
          ioselector.clearConnect(this)
          result.done(Left(Signal(ex)))
          return
      }

      ioselector.clearConnect(this)

      /**
       * Allocate receive and send buffers for the socket
       */
      val rcvBuf = ByteBuffer.allocate(niochan.socket.getReceiveBufferSize)
      val sndBuf = ByteBuffer.allocate(niochan.socket.getSendBufferSize)

      /**
       * Create a surrogate socket
       */
      val s = StreamSocket(niochan, SocketHandle(niochan.socket), ioselector, rcvBuf, sndBuf)
      result.success_!(s)
    }
  }
}

