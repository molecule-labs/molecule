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

package molecule.examples.io.stopwatch

case class DisplayTime(hr: Int = 0, min: Int = 0, sec: Int = 0, fract: Int = 0) {

  import DisplayTime._

  /**
   * Increment the time by 100ms (1 fract)
   */
  def increment(): DisplayTime =
    if (fract < 9)
      copy(fract = fract + 1)
    else if (sec < 59)
      copy(sec = sec + 1, fract = 0)
    else if (min < 59)
      copy(min = min + 1, sec = 0, fract = 0)
    else if (hr < 99)
      copy(hr = hr + 1, min = 0, sec = 0, fract = 0)
    else
      DisplayTime() //wrap

  def getDisplay(): String = {
    val padhr = if (hr > 9) EMPTY else ZERO
    val padmin = if (min > 9) EMPTY else ZERO
    val padsec = if (sec > 9) EMPTY else ZERO
    new StringBuffer().append(padhr).append(hr).append(DELIM).
      append(padmin).append(min).append(DELIM).append(padsec).
      append(sec).append(DOT).append(fract).toString();
  }
}

object DisplayTime {
  val DELIM = ":"; val DOT = "."; val EMPTY = ""; val ZERO = "0";
}
