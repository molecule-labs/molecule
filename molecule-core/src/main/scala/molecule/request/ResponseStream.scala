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
 * Base trait for messages that expect one or more response(s).
 */
trait ResponseStream[-A] {

  /**
   * Channel on which to stream response messages.
   */
  def rchan: ResponseStreamChannel[A]

}

object ResponseStream extends LowPrioMessageImplicits {
  private[this] object ResponseStreamMessageErasure extends Message[ResponseStream[Any]] {
    def poison(m: ResponseStream[Any], signal: Signal) = m.rchan.close(signal)
  }

  implicit def responseStreamIsMessage[R <: ResponseStream[_]]: Message[R] = ResponseStreamMessageErasure.asInstanceOf[Message[R]]

}
