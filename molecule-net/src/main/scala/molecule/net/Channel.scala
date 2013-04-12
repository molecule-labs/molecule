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

import java.nio.channels.{ SelectableChannel => JSelectableChannel }

/**
 * Minimal interface for selectable channel
 *
 */
abstract class Channel {

  /**
   * The underlying selectable channel
   */
  protected[net] def niochan: JSelectableChannel

  def shutdown(): Unit =
    niochan.close()

  def shutdown(signal: Signal): Unit =
    shutdown()
}

object Channel {

  implicit val channelIsMessage: Message[Channel] = new Message[Channel] {
    def poison(m: Channel, signal: Signal) = {
      m.shutdown(signal)
    }
  }

}