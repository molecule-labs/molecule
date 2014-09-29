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

  /* 
   * package private method to create a String from an Array of Char
   * The implementation tries to use the best available method of the JRE
   * and defines newString best on an initialisation test.
   * 
   * I.s.o using the standard public constructor of java.lang.String
   *    String(byte[] bytes, int offset, int length) 
   *        Constructs a new String by decoding the specified subarray of bytes using the platform's default charset.
   * we try to use the package private constructor which shares value array for speed.
   *        String(int offset, int count, char value[]) 
   *
   * This is possible on JDK1.6 (depending of the platform);
   * on JDK1.7 the latter method is deprecated and its implementation is calling the standard public constructor;
   * on JDK1.8 the latter method is not defined.
   */
  private[parsers] lazy val newString: (Int, Int, Array[Char]) => String = {
    import scala.collection.JavaConversions._

    classOf[String].getDeclaredConstructors().find(c => c.getModifiers == 0 && c.getGenericParameterTypes.length == 3) match {
      case None =>
        // method not found, so probably we run on JDK1.8 or above
        // let's use the normal constructor
        (offset, length, value) => new String(value, offset, length)
      case Some(c) =>
        // Yes, constructor found, let's make it accessible (overruling the package private declaration)
        c.setAccessible(true)

        // Let's see if it works as expected 
        try {
          val s = c.newInstance(0: java.lang.Integer, 2: java.lang.Integer, Array('h', 'i'))
          assert(s == "hi")
          // OK, fine
          (offset, length, value) =>
            c.newInstance(offset: java.lang.Integer, length: java.lang.Integer, value).asInstanceOf[String]
        } catch {
          case e: Throwable =>
            // whoops, better not to use it.
            // System.err.println("Warning: current platform does not support zero copy strings")
            (offset, length, value) => new String(value, offset, length)
        }
    }
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