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
package request

/**
 * Specialisation trait for messages that expect a single response(s).
 */
trait Response[-A] {

  /**
   * Channel on which to send the response messages.
   */
  def rchan: ResponseChannel[A]

}

object Response extends LowPrioMessageImplicits {

  private[this] object ResponseMessageErasure extends Message[Response[Any]] {
    def poison(m: Response[Any], signal: Signal) = m.rchan.failure_!(signal)
  }

  implicit def responseIsMessage[R <: Response[_]]: Message[R] = ResponseMessageErasure.asInstanceOf[Message[R]]

}