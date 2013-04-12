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

/**
 * Socket configuration class.
 *
 * @author Sebastien Bocq
 */
class SocketConfig(
    val opts: Seq[SocketOption]) {

  /**
   * Apply configuration to a socket
   */
  def apply(socket: java.net.Socket) =
    opts.foreach(opt => opt(socket))

}

object SocketConfig {

  def unapply(sc: SocketConfig) = Some(sc.opts)

  def apply(opts: SocketOption*): SocketConfig = new SocketConfig(opts)

}