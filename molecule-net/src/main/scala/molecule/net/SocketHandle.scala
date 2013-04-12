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

import java.net.{ SocketAddress, InetAddress }

/**
 * Class providing general information about a Molecule socket.
 *
 */
abstract class SocketHandle {
  /**
   * Returns the address to which this socket is connected.
   */
  def getInetAddress(): InetAddress

  /**
   * Gets the local address to which the socket is bound.
   */
  def getLocalAddress(): InetAddress

  /**
   * Returns the port number on the local host to which this socket is bound.
   */
  def getLocalPort(): Int

  /**
   * Returns the address of the endpoint this socket is bound to, or null if it is not bound yet.
   */
  def getLocalSocketAddress(): SocketAddress

  /**
   * Returns the remote port for this socket.
   */
  def getPort(): Int

  /**
   * Get value of the SO_RCVBUF option for this DatagramSocket, that is the buffer size used by the platform for input on this DatagramSocket.
   */
  def getReceiveBufferSize(): Int

  /**
   * Returns the address of the endpoint this socket is connected to, or null if it is unconnected.
   */
  def getRemoteSocketAddress(): SocketAddress

  /**
   * Tests if SO_REUSEADDR is enabled.
   */
  def getReuseAddress(): Boolean

  /**
   * Get value of the SO_SNDBUF option for this DatagramSocket, that is the buffer size used by the platform for output on this DatagramSocket.
   */
  def getSendBufferSize(): Int

  /**
   * Retrieve setting for SO_TIMEOUT.
   */
  def getSoTimeout(): Int

  /**
   * Gets traffic class or type-of-service in the IP datagram header for packets sent from this DatagramSocket.
   */
  def getTrafficClass(): Int

  /**
   * Returns the binding state of the socket.
   */
  def isBound(): Boolean

  /**
   * Returns whether the socket is closed or not.
   */
  def isClosed(): Boolean

  /**
   * Returns the connection state of the socket.
   */
  def isConnected(): Boolean

}

/**
 * Factory methods for creating Socket handles.
 *
 * @author Sebastien Bocq
 */
object SocketHandle {
  import java.net.{ Socket, DatagramSocket }

  def apply(s: Socket): SocketHandle = new SocketHandle {
    def getInetAddress(): InetAddress = s.getInetAddress
    def getLocalAddress(): InetAddress = s.getLocalAddress
    def getLocalPort(): Int = s.getLocalPort
    def getLocalSocketAddress(): SocketAddress = s.getLocalSocketAddress
    def getPort(): Int = s.getPort
    def getReceiveBufferSize(): Int = s.getReceiveBufferSize
    def getRemoteSocketAddress(): SocketAddress = s.getRemoteSocketAddress
    def getReuseAddress(): Boolean = s.getReuseAddress
    def getSendBufferSize(): Int = s.getSendBufferSize
    def getSoTimeout(): Int = s.getSoTimeout
    def getTrafficClass(): Int = s.getTrafficClass
    def isBound(): Boolean = s.isBound
    def isClosed(): Boolean = s.isClosed
    def isConnected(): Boolean = s.isConnected
  }

  def apply(s: DatagramSocket): SocketHandle = new SocketHandle {
    def getInetAddress(): InetAddress = s.getInetAddress
    def getLocalAddress(): InetAddress = s.getLocalAddress
    def getLocalPort(): Int = s.getLocalPort
    def getLocalSocketAddress(): SocketAddress = s.getLocalSocketAddress
    def getPort(): Int = s.getPort
    def getReceiveBufferSize(): Int = s.getReceiveBufferSize
    def getRemoteSocketAddress(): SocketAddress = s.getRemoteSocketAddress
    def getReuseAddress(): Boolean = s.getReuseAddress
    def getSendBufferSize(): Int = s.getSendBufferSize
    def getSoTimeout(): Int = s.getSoTimeout
    def getTrafficClass(): Int = s.getTrafficClass
    def isBound(): Boolean = s.isBound
    def isClosed(): Boolean = s.isClosed
    def isConnected(): Boolean = s.isConnected
  }

}