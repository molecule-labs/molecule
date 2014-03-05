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

package molecule.parsers

package object utils {
  import scala.collection.mutable.Builder

  /**
   * Assemble a collection of type To from a reversed list of elements
   */
  def assemble[Elem, To](racc: List[Elem], builder: Builder[Elem, To]): To = {
    val r = racc.foldRight(builder)((prev, last) => {
      builder += prev
    })
    r.result
  }

  private[parsers] lazy val newString: (Int, Int, Array[Char]) => String = {
    import scala.collection.JavaConversions._
    val c = classOf[String].getDeclaredConstructors().find(c => c.getModifiers == 0 && c.getGenericParameterTypes.length == 3).get
    c.setAccessible(true)

    // Dummy test 
    val zeroCopyStrings: Boolean = try {
      val s = c.newInstance(0: java.lang.Integer, 2: java.lang.Integer, Array('h', 'i'))
      assert(s == "hi")
      true
    } catch {
      case e =>
        System.err.println("Warning: current platform does not support zero copy strings")
        false
    }

    if (!zeroCopyStrings)
      (offset, length, value) => new String(value, offset, length)
    else
      (offset, length, value) =>
        c.newInstance(offset: java.lang.Integer, length: java.lang.Integer, value).asInstanceOf[String]
  }

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

}