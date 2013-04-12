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

import java.net.{ Socket => JSocket }
import java.net.ServerSocket
import java.net.{ DatagramSocket => JDatagramSocket }
import java.net.MulticastSocket

/**
 * Socket options.
 */
trait SocketOption {
  /**
   * Apply options to a Java Socket
   */
  def apply(s: JSocket): Unit
}

object SocketOption {

  trait ServerSocketOption {
    def apply(s: ServerSocket): Unit
  }

  trait MulticastSocketOption {
    def apply(s: MulticastSocket): Unit
  }

  trait DatagramSocketOption extends MulticastSocketOption {
    def apply(s: JDatagramSocket): Unit
    final def apply(s: MulticastSocket): Unit = apply(s.asInstanceOf[JDatagramSocket])
  }

  /**
   * Enable/disable TCP_NODELAY (disable/enable Nagle's algorithm).
   *  Valid for (client) Sockets.
   */
  case class TCP_NODELAY(on: Boolean) extends SocketOption {
    def apply(socket: JSocket) = socket.setTcpNoDelay(on)
  }

  /**
   * Enable/disable SO_LINGER with the specified linger time in seconds.
   * Valid for (client) Sockets.
   */
  case class SO_LINGER(on: Boolean, linger: Int) extends SocketOption {
    def apply(socket: JSocket) = socket.setSoLinger(on, linger)
  }

  /**
   * SO_TIMEOUT
   *  Enable/disable SO_TIMEOUT with the specified timeout, in milliseconds.
   *  Valid for all sockets: Socket, ServerSocket, JDatagramSocket.
   * Ignored by NIO
   */

  /**
   * SO_BINDADDR
   *  Fetch the local address binding of a socket.
   *  Valid for Socket, ServerSocket, JDatagramSocket.
   * Access through Socket API
   */

  /**
   * Enable reuse address for a socket.
   * Valid for Socket, ServerSocket, JDatagramSocket.
   */
  case class SO_REUSEADDR(on: Boolean) extends SocketOption with ServerSocketOption with DatagramSocketOption {
    def apply(socket: JSocket) = socket.setReuseAddress(on)
    def apply(socket: ServerSocket) = socket.setReuseAddress(on)
    def apply(socket: JDatagramSocket) = socket.setReuseAddress(on)
  }

  /**
   * Enables a socket to send broadcast messages.
   * Valid for JDatagramSocket.
   */
  case class SO_BROADCAST(on: Boolean) extends DatagramSocketOption {
    def apply(socket: JDatagramSocket) = socket.setBroadcast(on)
  }

  /**
   * Set a hint the size of the underlying buffers for outgoing network I/O.
   * Valid for Socket, JDatagramSocket.
   */
  case class SO_SNDBUF(size: Int) extends SocketOption with DatagramSocketOption {
    def apply(socket: JSocket) = socket.setSendBufferSize(size)
    def apply(socket: JDatagramSocket) = socket.setSendBufferSize(size)
  }

  /**
   * Set the size of the buffer actually used by the platform when receiving in data on this socket.
   *  Valid for all sockets: Socket, ServerSocket, JDatagramSocket.
   */
  case class SO_RCVBUF(size: Int) extends SocketOption with ServerSocketOption with DatagramSocketOption {
    def apply(socket: JSocket) = socket.setReceiveBufferSize(size)
    def apply(socket: ServerSocket) = socket.setReceiveBufferSize(size)
    def apply(socket: JDatagramSocket) = socket.setReceiveBufferSize(size)
  }

  /**
   * Turn on socket keepalive.
   * Valid for Socket.
   */
  case class SO_KEEPALIVE(on: Boolean) extends SocketOption {
    def apply(socket: JSocket) = socket.setKeepAlive(on)
  }

  /**
   * Enable inline reception of TCP urgent data.
   * Valid for Socket.
   */
  case class SO_OOBINLINE(on: Boolean) extends SocketOption {
    def apply(socket: JSocket) = socket.setOOBInline(on)
  }

  /**
   * IP_MULTICAST_IF
   * Specify the outgoing interface for multicast packets (on multihomed hosts).
   * Valid for MulticastSockets.
   * Access through MuticastSocket API
   */

  /**
   * Enables or disables local loopback of multicast datagrams.
   * Valid for MulticastSocket.
   */
  case class IP_MULTICAST_LOOP(on: Boolean) extends MulticastSocketOption {
    def apply(socket: MulticastSocket) = socket.setLoopbackMode(on)
  }

  /**
   * Sets the type-of-service or traffic class field in the IP header for a TCP or UDP socket.
   * Valid for Socket, JDatagramSocket
   */
  case class IP_TOS(tc: Int) extends SocketOption with DatagramSocketOption {
    def apply(socket: JSocket) = socket.setTrafficClass(tc)
    def apply(socket: JDatagramSocket) = socket.setTrafficClass(tc)
  }

}