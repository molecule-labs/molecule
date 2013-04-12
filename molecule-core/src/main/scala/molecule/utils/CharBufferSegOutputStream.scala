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
package utils

import java.io.OutputStream
import scala.collection.mutable.ListBuffer
import seg.Seg

/**
 * Contrarily to the regular CharArrayOutputStream this class
 * accumulate byte buffers into a list segment and assumes a
 * thread-safe environment and immutability which permit
 * to optimize the code by removing synchronization and defense
 * copying.
 *
 */
class CharBufferSegOutputStream {
  import java.nio.CharBuffer

  /**
   * The buffer where data is stored.
   */
  protected var buffer: Seg[CharBuffer] = Seg.empty

  /**
   * The number of valid bytes in the buffer.
   */
  protected var ccount: Int = 0

  def write(b: Array[Char]): Unit = {
    buffer :+= CharBuffer.wrap(b)
    ccount += b.length
  }

  def write(s: String): Unit = {
    buffer :+= CharBuffer.wrap(s)
    ccount += s.length
  }

  /**
   * Writes <code>len</code> bytes from the specified byte array
   * starting at offset <code>off</code> to this byte array output stream.
   *
   * @param   b     the data.
   * @param   off   the start offset in the data.
   * @param   len   the number of bytes to write.
   */
  def write(b: Array[Char], off: Int, len: Int) {
    if ((off < 0) || (off > b.length) || (len < 0) ||
      ((off + len) > b.length) || ((off + len) < 0)) {
      throw new IndexOutOfBoundsException();
    } else if (len == 0) {
      return ;
    }
    ccount += len
    if (off == 0 && len == b.length) {
      buffer :+= CharBuffer.wrap(b)
    } else {
      val part = new Array[Char](len)
      Array.copy(b, off, part, 0, len);
      buffer :+= CharBuffer.wrap(part)
    }
  }

  /**
   * Resets the <code>count</code> field of this byte array output
   * stream to zero, so that all currently accumulated output in the
   * output stream is discarded. The output stream can be used again,
   * reusing the already allocated buffer space.
   *
   * @see     java.io.CharArrayInputStream#count
   */
  def reset() {
    buffer = Seg.empty
    ccount = 0;
  }

  /**
   * Creates a newly allocated byte array. Its size is the current
   * size of this output stream and the valid contents of the buffer
   * have been copied into it.
   *
   * @return  the current contents of this output stream, as a byte array.
   * @see     java.io.CharArrayOutputStream#size()
   */
  def toCharArray(): Array[Char] = {
    if (buffer.length == 1) {
      return buffer.head.array
    }

    val r = new Array[Char](ccount)
    var position = 0
    buffer foreach { part =>
      System.arraycopy(r, position, part.array, 0, part.remaining);
      position += part.remaining
    }
    buffer = Seg.empty
    buffer :+= CharBuffer.wrap(r)
    r
  }

  import seg._

  /**
   * Returns the number of byt with the list of byte arrays
   * that have been written to this output stream.
   */
  def result(): Seg[CharBuffer] =
    buffer

  /**
   * Returns the current size of the buffer.
   *
   * @return  the value of the <code>count</code> field, which is the number
   *          of valid bytes in this output stream.
   * @see     java.io.CharArrayOutputStream#count
   */
  def size(): Int = {
    ccount;
  }

  /**
   * Converts the buffer's contents into a string decoding bytes using the
   * platform's default character set. The length of the new <tt>String</tt>
   * is a function of the character set, and hence may not be equal to the
   * size of the buffer.
   *
   * <p> This method always replaces malformed-input and unmappable-character
   * sequences with the default replacement string for the platform's
   * default character set. The {@linkplain java.nio.charset.CharsetDecoder}
   * class should be used when more control over the decoding process is
   * required.
   *
   * @return String decoded from the buffer's contents.
   * @since  JDK1.1
   */
  override def toString = new String(toCharArray)

}
