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

import channel.{ IChan, OChan, RIChan }, java.nio.ByteBuffer, java.net.{ SocketAddress, InetSocketAddress, DatagramPacket }, process._
import platform.Platform
import SocketOption.{ ServerSocketOption, DatagramSocketOption }

/**
 * This class encapsulates a selector thread that dispatches tasks on sockets
 * in a non-blocking manner. The number of connection
 * that can be handled by a selector is limited to FD_SETSIZE, which is OS specific.
 * See http://www.kegel.com/c10k.html#nb.select.
 *
 * See also how to change registry settings on windows here:
 * http://www.gridgainsystems.com/wiki/display/GG15UG/Troubleshooting#Troubleshooting-java.net.BindExceptionOnWindows
 *
 * If you want to handle many connections, either increase the value or
 * create several `NetSystem`s (last solution scales much better in terms
 * of performance).
 *
 */
abstract class NetSystem {

  // Tcp Client methods

  /**
   * Return the platform to which this connector is attached to.
   *
   * All process instances created by launchTcpClient and launchTcpServer
   * are launched over this platform.
   *
   * @return the underlying platform.
   */
  def platform: Platform

  /**
   * Create a TCP connection to a remote server.
   *
   * @param hostName the remote server host name
   * @param port the remote server port
   * @param opts  a list of socket options.
   *
   * @return a RIChan returning the client socket once it is connected.
   */
  def tcpConnect(hostName: String, port: Int, opts: SocketOption*): RIChan[Socket[ByteBuffer]] =
    tcpConnect(TcpClientConfig(hostName, port, opts: _*))

  /**
   * Create a TCP connection to a remote server.
   *
   * @param addr the remote server address
   * @param opts  a list of socket options.
   *
   * @return a RIChan returning the client socket once it is connected.
   */
  def tcpConnect(addr: InetSocketAddress, opts: SocketOption*): RIChan[Socket[ByteBuffer]] =
    tcpConnect(TcpClientConfig(addr, opts: _*))

  /**
   * Create a TCP connection to a remote server.
   *
   * @param config the client configuration.
   *
   * @return a tuple with the local address the client is bound to and
   *         a RIChan returning the client socket once it is connected.
   */
  def tcpConnect(config: TcpClientConfig): RIChan[Socket[ByteBuffer]]

  /**
   * Connect a TCP client process to a remote server.
   *
   * @param hostName the remote server host name
   * @param port the remote server port
   * @param process a process that takes network ByteBuffer's as input and output.
   *
   * @tparam R the termination result of the client process.
   *
   * @return a RIChan returning the termination result of the client process.
   */
  def launchTcpClient[R: Message](hostName: String, port: Int, process: NetworkHandler[R], opts: SocketOption*): RIChan[R] =
    launchTcpClient(TcpClientConfig(hostName, port, opts: _*), process)

  /**
   * Connect a TCP client process to a remote server.
   *
   * @param addr the remote server address
   * @param process a process that takes network ByteBuffer's as input and output.
   * @param opts socket options that must be applied to the client socket once it is connected
   *
   * @tparam R the termination result of the client process.
   *
   * @return a RIChan returning the termination result of the client process.
   */
  def launchTcpClient[R: Message](addr: InetSocketAddress, process: NetworkHandler[R], opts: SocketOption*): RIChan[R] =
    launchTcpClient(TcpClientConfig(addr, opts: _*), process)

  /**
   * Connect a TCP client process to a remote server.
   *
   * @param bindAddr the local address the client must be bound to
   * @param addr the remote server address
   * @param process a process that takes network ByteBuffer's as input and output.
   * @param opts socket options that must be applied to the client socket once it is connected
   *
   * @tparam R the termination result of the client process.
   *
   * @return a RIChan returning the termination result of the client process.
   */
  def launchTcpClient[R: Message](bindAddr: InetSocketAddress, addr: InetSocketAddress, process: NetworkHandler[R], opts: SocketOption*): RIChan[R] =
    launchTcpClient(TcpClientConfig(bindAddr, addr, opts: _*), process)

  /**
   * Connect a TCP client process to a remote server.
   *
   * @param config the client config
   * @param process a process that takes network ByteBuffer's as input and output.
   *
   * @tparam R the termination result of the client process.
   *
   * @return a tuple with the local address the client is bound to and
   *         a RIChan returning the termination result of the client process.
   */
  def launchTcpClient[R: Message](config: TcpClientConfig, process: NetworkHandler[R]): RIChan[R]

  // Tcp server methods

  /**
   * Create a TCP server channel.
   *
   * @return a tuple with the local socket address the server has been automatically
   *         bound to, and a stream of client sockets filled in progressively each time
   * 		 a new client connection is accepted.
   */
  def tcpAccept(): (InetSocketAddress, IChan[Socket[ByteBuffer]]) =
    tcpAccept(TcpServerConfig())

  /**
   * Create a TCP server channel.
   *
   * @param hostName the local server interface
   * @param port the local server port
   * @param opts a list of socket options applied to each client socket accepted.
   *
   * @return a stream of client sockets filled in progressively each time
   * 		 a new client connection is accepted.
   */
  def tcpAccept(hostName: String, port: Int, opts: SocketOption*): IChan[Socket[ByteBuffer]] =
    tcpAccept(TcpServerConfig(hostName, port, opts: _*))._2

  /**
   * Create a TCP server channel.
   *
   * @param hostName the local server interface
   * @param port the local server port
   * @param sopts a list of server socket options applied to the server socket.
   * @param opts a list of socket options applied to each client socket accepted.
   *
   * @return a stream of client sockets filled in progressively each time
   * 		 a new client connection is accepted.
   */
  def tcpAccept(hostName: String, port: Int, sopts: List[ServerSocketOption], opts: SocketOption*): IChan[Socket[ByteBuffer]] =
    tcpAccept(TcpServerConfig(hostName, port, TcpServerConfig.BACKLOG_DEFAULT, sopts, opts: _*))._2

  /**
   * Create a TCP server channel.
   *
   * @param config a TCP server configuration
   *
   * @return a tuple with the local socket address the server has been
   *         bound to, and a stream of client sockets filled in progressively each time
   * 		 a new client connection is accepted.
   */
  def tcpAccept(config: TcpServerConfig): (InetSocketAddress, IChan[Socket[ByteBuffer]]) = {
    val (i, o) = channel.Chan.mk[Socket[ByteBuffer]]()
    val addr = tcpAccept(config, o)
    (addr, i)
  }

  /**
   * Create a TCP server channel.
   *
   * @param config a TCP server configuration
   * @param a channel on which new socket connections must be posted.
   *
   * @return the local socket address the server has been bound to.
   */
  def tcpAccept(config: TcpServerConfig, ochan: OChan[Socket[ByteBuffer]]): InetSocketAddress

  /**
   * Create a TCP server process that spawns a new servlet to handle each new
   * client connection.
   *
   * @param handler a process type from which new servlets are instantiated.
   *
   * @return a tuple with the local socket address the server has been
   *         bound to, and a result channel that terminates when the server
   *         socket is closed.
   */
  //def launchTcpServer(handler:NetworkHandler[Unit]):(InetSocketAddress, RIChan[Unit]) =
  //	launchTcpServer(TcpServerConfig(), handler)

  /**
   * Create a TCP server process that spawns a new servlet to handle each new
   * client connection.
   *
   * @param opts a list of socket options applied to each client socket accepted.
   * @param handler a process type from which new servlets are instantiated.
   *
   * @return the socket address the server has been bound to.
   */
  def launchTcpServer(handler: NetworkHandler[Unit], opts: SocketOption*): InetSocketAddress =
    launchTcpServer(TcpServerConfig(opts: _*), handler)

  /**
   * Create a TCP server process that spawns a new servlet to handle each new
   * client connection.
   *
   * @param hostName the local server interface
   * @param port the local server port
   * @param opts a list of socket options applied to each client socket accepted.
   * @param handler a process type from which new servlets are instantiated.
   *
   * @return the socket address the server has been bound to.
   */
  def launchTcpServer(hostName: String, port: Int, handler: NetworkHandler[Unit], opts: SocketOption*): InetSocketAddress =
    launchTcpServer(TcpServerConfig(hostName, port, opts: _*), handler)

  /**
   * Create a TCP server process that spawns a new servlet to handle each new
   * client connection.
   *
   * @param hostName the local server interface
   * @param port the local server port
   * @param sopts a list of server socket options applied to the server socket.
   * @param opts a list of socket options applied to each client socket accepted.
   * @param handler a process type from which new servlets are instantiated.
   *
   * @return the socket address the server has been bound to.
   */
  def launchTcpServer(hostName: String, port: Int, handler: NetworkHandler[Unit], sopts: List[ServerSocketOption], opts: SocketOption*): InetSocketAddress =
    launchTcpServer(TcpServerConfig(hostName, port, TcpServerConfig.BACKLOG_DEFAULT, sopts, opts: _*), handler)

  /**
   * Create a TCP server process that spawns a new servlet to handle each new
   * client connection.
   *
   * @param addr the local server address on which the server accepts connections
   * @param handler a process type from which new servlets are instantiated.
   * @param opts a list of socket options applied to each client socket accepted.
   *
   * @return the socket address the server has been bound to.
   */
  def launchTcpServer(addr: InetSocketAddress, handler: NetworkHandler[Unit], opts: SocketOption*): InetSocketAddress =
    launchTcpServer(TcpServerConfig(addr, TcpServerConfig.BACKLOG_DEFAULT, List(), opts: _*), handler)

  /**
   * Create a TCP server process that spawns a new servlet to handle each new
   * client connection.
   *
   * @param addr the local server address on which the server accepts connections
   * @param sopts a list of server socket options applied to the server socket.
   * @param opts a list of socket options applied to each client socket accepted.
   * @param handler a process type from which new servlets are instantiated.
   *
   * @return the socket address the server has been bound to.
   */
  def launchTcpServer(addr: InetSocketAddress, handler: NetworkHandler[Unit], sopts: List[ServerSocketOption], opts: SocketOption*): InetSocketAddress =
    launchTcpServer(TcpServerConfig(addr, TcpServerConfig.BACKLOG_DEFAULT, sopts, opts: _*), handler)

  /**
   * Create a TCP server process that spawns a new servlet to handle each new
   * client connection.
   *
   * @param config a TCP server config
   * @param handler a process type from which new servlets are instantiated.
   *
   * @return the socket address the server has been bound to.
   */
  def launchTcpServer(config: TcpServerConfig, handler: NetworkHandler[Unit]): InetSocketAddress

  // UDP end-point methods

  /**
   * Create a UDP datagram socket.
   *
   * @param hostName the local name of the interface
   * @param port the local port this socket is bound to
   * @param opts a list of datagram socket options
   *
   * @return the datagram socket.
   */
  def udpBind(hostName: String, port: Int, opts: DatagramSocketOption*): Socket[DatagramPacket] =
    udpBind(new InetSocketAddress(hostName, port), opts: _*)

  /**
   * Create a UDP datagram socket.
   *
   * @param address the local address this socket is bound to
   * @param opts a list of datagram socket options
   *
   * @return the datagram socket.
   */
  def udpBind(address: SocketAddress, opts: DatagramSocketOption*): Socket[DatagramPacket]

  /**
   * Create a "connected" UDP socket restricted to a remote peer.
   *
   * @param localAddress the local address this socket is bound to
   * @param remote the peer address
   * @param opts a list of datagram socket options
   *
   * @return the datagram socket.
   */
  def udpBind(local: SocketAddress, remote: SocketAddress, opts: DatagramSocketOption*): Socket[DatagramPacket]

  /**
   * Shutdown and close all resources allocated by the platform (e.g. the selector thread).
   */
  def shutdownNow(): Unit

  /**
   * Shutdown.
   *
   * Close all threading resources allocated by the platform (e.g. the selector thread).
   */
  def shutdown(): Unit

}

/**
 * Factory for NetSystems
 */
object NetSystem {

  def apply(platform: Platform): NetSystem = new NetSystemImpl(platform)

  private[this] class NetSystemImpl(val platform: Platform) extends NetSystem {

    private[this] final val selector = IOSelector()

    def shutdownNow(): Unit =
      selector.shutdownNow()

    def shutdown(): Unit =
      selector.shutdown()

    def tcpConnect(config: TcpClientConfig): RIChan[Socket[ByteBuffer]] =
      TcpConnectChannel(selector, config)

    def launchTcpClient[R: Message](config: TcpClientConfig, handler: NetworkHandler[R]): RIChan[R] = {
      val cnxCh = tcpConnect(config)
      val rchan = cnxCh.flatMap(socket => platform.launch(handler(socket.ichan, socket.ochan))).fire()
      rchan
    }

    def tcpAccept(config: TcpServerConfig, ochan: OChan[Socket[ByteBuffer]]): InetSocketAddress =
      TcpAcceptChannel(selector, config, ochan)

    import stream._

    def launchTcpServer(config: TcpServerConfig, handler: NetworkHandler[Unit]): InetSocketAddress = {
      val (addr, acceptCh) = tcpAccept(config)
      platform.launch(acceptCh.foreach(socket => platform.launch(handler(socket.ichan, socket.ochan))))
      addr
    }

    def udpBind(address: SocketAddress, opts: DatagramSocketOption*): Socket[DatagramPacket] =
      UDP.bind(selector, address, opts: _*)

    def udpBind(local: SocketAddress, remote: SocketAddress, opts: DatagramSocketOption*): Socket[DatagramPacket] =
      UDP.bind(selector, local, remote, opts: _*)

  }
}
