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

import java.net.InetSocketAddress
import SocketOption.ServerSocketOption

/**
 * Server channel configuration class.
 *
 * If the address is null, then the system will pick up an ephemeral port and a valid local
 * address to bind the socket.
 *
 * The backlog argument must be a positive value greater than 0. If the value passed if equal or less than 0, then the default value will be assumed.
 */
class TcpServerConfig(
    val address: Option[InetSocketAddress],
    val backlog: Int,
    val opts: List[ServerSocketOption],
    val socketConfig: SocketConfig,
    val maxSockets: Int) {

  /**
   * Apply the configuration to a server socket
   */
  def apply(socket: java.net.ServerSocket) =
    opts.foreach(opt => opt(socket))

}

object TcpServerConfig {

  val MAX_SOCKET_DEFAULT = 100000
  val BACKLOG_DEFAULT = 50

  def unapply(sc: TcpServerConfig) = Some(sc.address, sc.opts, sc.socketConfig, sc.maxSockets)

  def apply(opts: SocketOption*): TcpServerConfig =
    apply(None, BACKLOG_DEFAULT, List(), SocketConfig(opts: _*), MAX_SOCKET_DEFAULT)

  def apply(hostName: String, port: Int, opts: SocketOption*): TcpServerConfig =
    apply(new InetSocketAddress(hostName, port), BACKLOG_DEFAULT, opts: _*)

  def apply(hostName: String, port: Int, backlog: Int, opts: SocketOption*): TcpServerConfig =
    apply(Some(new InetSocketAddress(hostName, port)), backlog, List(), SocketConfig(opts: _*), MAX_SOCKET_DEFAULT)

  def apply(address: InetSocketAddress, opts: SocketOption*): TcpServerConfig =
    apply(address, BACKLOG_DEFAULT, opts: _*)

  def apply(address: InetSocketAddress, backlog: Int, opts: SocketOption*): TcpServerConfig =
    apply(Some(address), backlog, List(), SocketConfig(opts: _*), MAX_SOCKET_DEFAULT)

  def apply(address: InetSocketAddress, backlog: Int, serverOpts: List[ServerSocketOption], opts: SocketOption*): TcpServerConfig =
    apply(Some(address), backlog, serverOpts, SocketConfig(opts: _*), MAX_SOCKET_DEFAULT)

  def apply(hostName: String, port: Int, backlog: Int, serverOpts: List[ServerSocketOption], socketOpts: SocketOption*): TcpServerConfig =
    apply(Some(new InetSocketAddress(hostName, port)), backlog, serverOpts, SocketConfig(socketOpts: _*), MAX_SOCKET_DEFAULT)

  def apply(address: Option[InetSocketAddress],
    backlog: Int,
    opts: List[ServerSocketOption],
    socketConfig: SocketConfig,
    maxSockets: Int): TcpServerConfig = new TcpServerConfig(address, backlog, opts, socketConfig, maxSockets)

}