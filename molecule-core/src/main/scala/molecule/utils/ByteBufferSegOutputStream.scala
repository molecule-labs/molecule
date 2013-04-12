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
import java.io.ByteArrayOutputStream
import scala.collection.mutable.ListBuffer
import seg.Seg

/**
 * Contrarily to the regular ByteArrayOutputStream this class
 * accumulate byte buffers into a list segment and assumes a
 * thread-safe environment and immutability which permit
 * to optimize the code by removing synchronization and defense
 * copying.
 *
 */
class ByteBufferSegOutputStream extends OutputStream {
  import java.nio.ByteBuffer

  /**
   * The buffer where data is stored.
   */
  protected var buffer: Seg[ByteBuffer] = Seg.empty

  protected var baos: ByteArrayOutputStream = null

  /**
   * The number of valid bytes in the buffer.
   */
  protected var bcount: Int = 0

  @inline
  private[this] final def compact() {
    if (baos != null) {
      val part = baos.toByteArray
      buffer :+= ByteBuffer.wrap(part)
      bcount += part.length
      baos = null
    }
  }

  /**
   * Writes the specified byte to this byte array output stream.
   *
   * @param   b   the byte to be written.
   */
  def write(b: Int) {
    // Switch to byte array outputstream mode
    if (baos == null)
      baos = new ByteArrayOutputStream
    baos.write(b)
  }

  override def write(b: Array[Byte]) = {
    buffer :+= ByteBuffer.wrap(b)
    bcount += b.length
  }

  def write(b: ByteBuffer) = {
    buffer :+= b
    bcount += b.remaining
  }

  /**
   * Writes <code>len</code> bytes from the specified byte array
   * starting at offset <code>off</code> to this byte array output stream.
   *
   * @param   b     the data.
   * @param   off   the start offset in the data.
   * @param   len   the number of bytes to write.
   */
  override def write(b: Array[Byte], off: Int, len: Int) {
    if ((off < 0) || (off > b.length) || (len < 0) ||
      ((off + len) > b.length) || ((off + len) < 0)) {
      throw new IndexOutOfBoundsException();
    } else if (len == 0) {
      return ;
    }
    compact()
    bcount += len
    if (off == 0 && len == b.length) {
      buffer :+= ByteBuffer.wrap(b)
    } else {
      val part = new Array[Byte](len)
      Array.copy(b, off, part, 0, len);
      buffer :+= ByteBuffer.wrap(part)
    }
  }

  /**
   * Writes the complete contents of this byte array output stream to
   * the specified output stream argument, as if by calling the output
   * stream's write method using <code>out.write(buf, 0, count)</code>.
   *
   * @param      out   the output stream to which to write the data.
   * @throws  IOException  if an I/O error occurs.
   */
  def writeTo(out: OutputStream) {
    compact()
    buffer foreach { part => out.write(part.array, 0, part.remaining) }
  }

  /**
   * Resets the <code>count</code> field of this byte array output
   * stream to zero, so that all currently accumulated output in the
   * output stream is discarded. The output stream can be used again,
   * reusing the already allocated buffer space.
   *
   * @see     java.io.ByteArrayInputStream#count
   */
  def reset() {
    buffer = Seg.empty
    baos = null
    bcount = 0;
  }

  /**
   * Creates a newly allocated byte array. Its size is the current
   * size of this output stream and the valid contents of the buffer
   * have been copied into it.
   *
   * @return  the current contents of this output stream, as a byte array.
   * @see     java.io.ByteArrayOutputStream#size()
   */
  def toByteArray(): Array[Byte] = {
    compact()
    if (buffer.length == 1) {
      return buffer.head.array
    }

    val r = new Array[Byte](bcount)
    var position = 0
    buffer foreach { part =>
      System.arraycopy(r, position, part.array, 0, part.remaining);
      position += part.remaining
    }
    buffer = Seg.empty
    buffer :+= ByteBuffer.wrap(r)
    r
  }

  /**
   * Returns the number of byt with the list of byte arrays
   * that have been written to this output stream.
   */
  def result(): Seg[ByteBuffer] = {
    compact()
    buffer
  }

  /**
   * Returns the current size of the buffer.
   *
   * @return  the value of the <code>count</code> field, which is the number
   *          of valid bytes in this output stream.
   * @see     java.io.ByteArrayOutputStream#count
   */
  def size(): Int = {
    compact()
    bcount;
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
  override def toString = new String(toByteArray, 0, bcount)

  /**
   * Converts the buffer's contents into a string by decoding the bytes using
   * the specified {@link java.nio.charset.Charset charsetName}. The length of
   * the new <tt>String</tt> is a function of the charset, and hence may not be
   * equal to the length of the byte array.
   *
   * <p> This method always replaces malformed-input and unmappable-character
   * sequences with this charset's default replacement string. The {@link
   * java.nio.charset.CharsetDecoder} class should be used when more control
   * over the decoding process is required.
   *
   * @param  charsetName  the name of a supported
   * 		    {@linkplain java.nio.charset.Charset </code>charset<code>}
   * @return String decoded from the buffer's contents.
   * @throws  UnsupportedEncodingException
   *             If the named charset is not supported
   * @since   JDK1.1
   */
  def toString(charsetName: String): String = new String(toByteArray, 0, bcount, charsetName)
}
