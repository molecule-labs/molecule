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
 * Some utilities.
 */
object Utils {

  import java.nio.ByteBuffer
  import java.nio.CharBuffer

  /**
   * Some utils
   */

  def showHex(in: Array[Byte]): String = {

    var ch = 0x00b;
    var i = 0;
    if (in == null || in.length <= 0)
      return "";

    val pseudo = Array[String]("0", "1", "2",
      "3", "4", "5", "6", "7", "8",
      "9", "A", "B", "C", "D", "E",
      "F");

    val out = new StringBuffer(in.length * 2);

    while (i < in.length) {
      ch = (in(i) & 0xF0) // Strip off high nibble
      ch = (ch >>> 4) // shift the bits down
      ch = (ch & 0x0F) // must do this is high order bit is on!
      out.append(pseudo(ch)) // convert the nibble to a String Character
      ch = (in(i) & 0x0F) // Strip off low nibble 
      out.append(pseudo(ch)) // convert the nibble to a String Character
      i += 1
    }

    val rslt = new String(out)

    return rslt

  }

  def bbToHexString(bb: ByteBuffer): String = showHex(bb)

  def showHex(bb: ByteBuffer): String = {

    var ch = 0x00b;
    var i = bb.position;
    if (bb == null || bb.remaining <= 0)
      return "";

    val pseudo = Array[String]("0", "1", "2",
      "3", "4", "5", "6", "7", "8",
      "9", "A", "B", "C", "D", "E",
      "F");

    val out = new StringBuffer(bb.remaining * 2);

    val in = bb.array
    while (i < bb.limit) {
      ch = (in(i) & 0xF0) // Strip off high nibble
      ch = (ch >>> 4) // shift the bits down
      ch = (ch & 0x0F) // must do this is high order bit is on!
      out.append(pseudo(ch)) // convert the nibble to a String Character
      ch = (in(i) & 0x0F) // Strip off low nibble 
      out.append(pseudo(ch)) // convert the nibble to a String Character
      i += 1
    }

    val rslt = new String(out)

    return rslt

  }

}