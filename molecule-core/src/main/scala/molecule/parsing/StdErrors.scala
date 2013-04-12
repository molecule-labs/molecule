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
package parsing

object StdErrors {

  /**
   * Input stream was closed before the last token was completely parsed
   */
  @inline
  final def prematureSignal[A](parserName: String, seg: Seg[A], signal: Signal): Fail =
    Fail(parserName, "premature " + signal + ", found only " + seg)

  /**
   * Found another token instead of the expected one
   */
  @inline
  final def mismatch[A](parserName: String, expected: A, found: A): Fail =
    Fail(parserName, "'" + expected + "' expected but " + found + " found")

  /**
   * Input stream was closed before finding the expected token
   */
  @inline
  final def prematureSignalMismatch[B, A](parserName: String, expected: B, rem: Seg[A], signal: Signal): Fail =
    Fail(parserName, "'" + expected + "' expected but only " + rem + " remaining before signal: " + signal)

  /**
   * Input stream was closed before finding the expected token
   */
  @inline
  final def prematureSignalTrailing[B, A](parserName: String, found: Seg[B], rem: Seg[A], signal: Signal): Fail =
    Fail(parserName, "found " + found + " expected but don't know what to do with remaining segment " + rem + " before signal: " + signal)

  /**
   * Did not found enough tokens because of a failure
   */
  @inline
  final def countNotReached[B](parserName: String, expected: Int, found: Seg[B], fail: => Fail): Fail =
    Fail(parserName, "'" + expected + "' elements expected but found " + found + " because of " + fail)

  /**
   * Repetition out of sync
   */
  @inline
  final def repOutOfSync[B](parserName: String, found: Seg[B], fail: => Fail): Fail =
    Fail(parserName, "found " + found + " but repetition got out of sync because of " + fail)
}
