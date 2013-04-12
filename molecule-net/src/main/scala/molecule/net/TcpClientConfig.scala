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

/**
 * Client channel configuration class.
 *
 */
class TcpClientConfig(
  val bindAddress: Option[InetSocketAddress],
  val remoteAddress: InetSocketAddress,
  opts: List[SocketOption]) extends SocketConfig(opts)

object TcpClientConfig {

  def unapply(sc: TcpClientConfig) = Some(sc.bindAddress, sc.remoteAddress, sc.opts)

  def apply(hostName: String, port: Int, opts: SocketOption*): TcpClientConfig =
    apply(new InetSocketAddress(hostName, port), opts: _*)

  def apply(address: InetSocketAddress, opts: SocketOption*) =
    new TcpClientConfig(None, address, List(opts: _*))

  def apply(bindAddress: InetSocketAddress, remoteAddress: InetSocketAddress, opts: SocketOption*) =
    new TcpClientConfig(Some(bindAddress), remoteAddress, List(opts: _*))

}

