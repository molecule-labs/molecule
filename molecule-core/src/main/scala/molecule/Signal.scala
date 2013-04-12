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
 * Supertype of all termination signals.
 */
abstract class Signal

object Signal {

  /**
   * Create a miscellaneous error message.
   *
   * @param message the error message.
   * @return an error signal
   */
  def apply(message: String): signals.ErrorSignal = signals.ErrorSignal(message)

  /**
   * Encapsulate a throwable into a signal.
   *
   * @param t the throwable.
   * @return a signal.
   */
  def apply(t: Throwable): Signal = signals.JThrowable(t)

  /**
   * Throw a signal as an exception.
   *
   * @param signal the signal.
   * @return nothing (it throws an `ExceptionSignal`).
   */
  def throwException(signal: Signal): Nothing = throw signals.SignalException(signal)
}

/**
 * Standard End-Of-Stream signal.
 */
case object EOS extends Signal
