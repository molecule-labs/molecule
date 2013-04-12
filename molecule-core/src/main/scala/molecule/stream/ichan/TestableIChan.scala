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
package stream
package ichan

/**
 * IChan that can be tested for the availability of data in memory.
 */
trait TestableIChan[+A] extends IChan[A] {

  /**
   * The test operation is idempotent meaning that it can be invoked multiple times.
   *
   * A test extracts data from its underlying ichan and invokes the ready function
   * on a buffered ichan if a segment is available or on the NilIChan if the
   * underlying ichan is closed.
   *
   * The contract is the following:
   * - Any channel buffering a non-empty segment is ready
   * - A channel that contains an empty segment followed by a terminated channel is ready
   * - A channel that contains an empty segment followed by a live channel is NOT ready
   */
  def test(t: UThread, ready: IChan[A] => Unit): Unit

}
