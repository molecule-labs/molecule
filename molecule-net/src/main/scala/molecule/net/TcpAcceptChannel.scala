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

import channel.OChan

/**
 * A server channel generates one socket per connection.
 */
private[net] abstract class TcpAcceptChannel extends Channel {

  private[net] def acceptReady()

}

/**
 * Factory methods for TCP server channels
 *
 */
object TcpAcceptChannel {

  /**
   * Create a TCP server channel.
   *
   * @param ioselector an IOSelector in charge of handling this server channel.
   * @param config a TCP server configuration
   *
   * @return a tuple with the local socket address the server has been
   *         bound to, and a stream of client sockets filled in progressively each time
   * 		 a new client connection is accepted.
   */
  def apply(ioselector: IOSelector, config: TcpServerConfig, ochan: OChan[Socket[ByteBuffer]]): InetSocketAddress = {
    val niochan = ServerSocketChannel.open()
    niochan.configureBlocking(false)
    val socket = niochan.socket
    if (config.address.isDefined)
      socket.bind(config.address.get, config.backlog)
    else
      socket.bind(null, config.backlog)

    config(socket)

    val addr = new InetSocketAddress(socket.getInetAddress(), socket.getLocalPort())

    new TcpAcceptChannelImpl(ioselector, niochan, config.socketConfig, ochan)
    addr
  }

  private[this] final class TcpAcceptChannelImpl(
      selector: IOSelector,
      val niochan: ServerSocketChannel,
      socketConfig: SocketConfig,
      private[this] final var OCHAN: OChan[Socket[ByteBuffer]]) extends TcpAcceptChannel { self =>

    // Create a new non-blocking server socket channel

    selector.schedule(new Runnable { def run = selector.registerAccept(self) })

    private[this] final var updateTask: Runnable = null

    private[this] final val update: OChan[Socket[ByteBuffer]] => Unit = { ochan =>
      submit(new Runnable { def run = TcpAcceptChannelImpl.this.OCHAN = ochan })
    }

    def submit(updateTask: Runnable): Unit = {
      if (Thread.currentThread.getId != selector.threadId)
        selector.schedule(new Runnable {
          def run = {
            updateTask.run()
            OCHAN match {
              case OChan(signal) =>
                poison(signal)
              case _ =>
                selector.registerAccept(self)
            }
          }
        })
      else
        this.updateTask = updateTask
    }

    /**
     * This method is always invoked by the Selector thread
     */
    private[net] def acceptReady() {
      val socketChannel = niochan.accept();
      socketChannel.configureBlocking(false);
      socketConfig(socketChannel.socket)

      val rcvBuf = ByteBuffer.allocate(socketChannel.socket.getReceiveBufferSize)
      val sndBuf = ByteBuffer.allocate(socketChannel.socket.getSendBufferSize)

      val s = StreamSocket(socketChannel, SocketHandle(socketChannel.socket), selector, rcvBuf, sndBuf)

      OCHAN.write(Seg(s), None, update)

      if (updateTask == null)
        selector.clearAccept(this)
      else {
        val tmp = updateTask
        updateTask = null
        tmp.run()
        OCHAN match {
          case OChan(signal) =>
            poison(signal)
          case _ => // acceptReadey will be called again            
        }
      }
    }

    /**
     * Always invoked with the selector thread
     */
    def poison(signal: Signal): Unit = {
      selector.clearAccept(self)
      niochan.close()
      OCHAN.close(signal)
    }
  }

}

