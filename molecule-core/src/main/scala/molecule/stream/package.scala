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

/**
 * Stream channel interfaces
 */
package object stream {

  /**
   * Create a stream input channel from a system input channel.
   *
   * @param ch the system-level channel
   *
   * @return the stream channel
   */
  implicit def liftIChan[A: Message](ch: channel.IChan[A]): IChan[A] =
    ichan.FrontIChan(ichan.BackIChan(ch))

  /**
   * Create a stream output channel from a system output channel.
   *
   * @param ch the system-level channel
   *
   * @return the stream output channel
   */
  implicit def liftOChan[A: Message](ch: channel.OChan[A]): OChan[A] =
    ochan.BackOChan(ch)

  implicit def liftOChanFactory[A: Message](factory: channel.OChanFactory[A]): ochan.OChanFactory[A] =
    ochan.OChanFactory.lift(factory)

}