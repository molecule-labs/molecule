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

package molecule.utils

import java.io.InputStream

/**
 * Class use for compatibility with existing blocking java serialization
 * frameworks e.g. Avro.
 *
 */
class ByteArrayListInputStream(count: Int, arrays: List[Array[Byte]]) extends InputStream {

  /**
   * The index of the next character to read from the input stream buffer.
   * This value should always be nonnegative
   * and not larger than the value of <code>count</code>.
   * The next byte to be read from the input stream buffer
   * will be <code>buf[pos]</code>.
   */
  private var pos = 0

  private var rem = arrays
  private var cursor = 0

  /**
   * Reads the next byte of data from this input stream. The value
   * byte is returned as an <code>int</code> in the range
   * <code>0</code> to <code>255</code>. If no byte is available
   * because the end of the stream has been reached, the value
   * <code>-1</code> is returned.
   * <p>
   * This <code>read</code> method
   * cannot block.
   *
   * @return  the next byte of data, or <code>-1</code> if the end of the
   *          stream has been reached.
   */
  def read(): Int = {
    if (pos < count) {
      val b = rem.head(cursor) & 0xff
      cursor += 1
      pos += 1
      if (cursor == rem.head.length) {
        if (pos < count) {
          rem = rem.tail
          cursor = 0
        } else {
          rem = Nil
        }
      }
      b
    } else
      -1
  }

  /**
   * Reads up to <code>len</code> bytes of data into an array of bytes
   * from this input stream.
   * If <code>pos</code> equals <code>count</code>,
   * then <code>-1</code> is returned to indicate
   * end of file. Otherwise, the  number <code>k</code>
   * of bytes read is equal to the smaller of
   * <code>len</code> and <code>count-pos</code>.
   * If <code>k</code> is positive, then bytes
   * <code>buf[pos]</code> through <code>buf[pos+k-1]</code>
   * are copied into <code>b[off]</code>  through
   * <code>b[off+k-1]</code> in the manner performed
   * by <code>System.arraycopy</code>. The
   * value <code>k</code> is added into <code>pos</code>
   * and <code>k</code> is returned.
   * <p>
   * This <code>read</code> method cannot block.
   *
   * @param   b     the buffer into which the data is read.
   * @param   off   the start offset in the destination array <code>b</code>
   * @param   len   the maximum number of bytes read.
   * @return  the total number of bytes read into the buffer, or
   *          <code>-1</code> if there is no more data because the end of
   *          the stream has been reached.
   * @throws  NullPointerException If <code>b</code> is <code>null</code>.
   * @throws  IndexOutOfBoundsException If <code>off</code> is negative,
   * <code>len</code> is negative, or <code>len</code> is greater than
   * <code>b.length - off</code>
   */
  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    if (b == null) {
      throw new NullPointerException();
    } else if (off < 0 || len < 0 || len > b.length - off) {
      throw new IndexOutOfBoundsException();
    }
    if (pos >= count) {
      return -1;
    }
    val _len = if (pos + len > count) count - pos else len
    if (_len <= 0) {
      return 0;
    }
    var i = 0
    while (i < _len) {
      val l = rem.head.length - cursor
      val rlen = _len - i
      if (l >= rlen) {
        Array.copy(rem.head, cursor, b, i, rlen)
        cursor += rlen
        if (cursor == rem.head.length) {
          cursor = 0
          rem = rem.tail
        }
        i += rlen
      } else {
        Array.copy(rem.head, cursor, b, i, l)
        cursor = 0
        rem = rem.tail
        i += l
      }
    }
    pos += _len;
    _len
  }

  /**
   * Skips <code>n</code> bytes of input from this input stream. Fewer
   * bytes might be skipped if the end of the input stream is reached.
   * The actual number <code>k</code>
   * of bytes to be skipped is equal to the smaller
   * of <code>n</code> and  <code>count-pos</code>.
   * The value <code>k</code> is added into <code>pos</code>
   * and <code>k</code> is returned.
   *
   * @param   n   the number of bytes to be skipped.
   * @return  the actual number of bytes skipped.
   */
  override def skip(_n: Long): Long = {
    if (pos + _n > count) {
      count - pos
    } else if (_n < 0) {
      0
    } else {
      val n = _n.toInt
      pos += n
      var i = 0
      while (i < n) {
        val l = rem.head.length - cursor
        val rlen = n - i
        if (l < rlen) {
          cursor = 0
          rem = rem.tail
          i += l
        } else if (l > rlen) {
          cursor += rlen
          return n
        } else if (l == rlen) {
          cursor = 0
          rem = rem.tail
          return n
        }
      }
      n
    }
  }

  /**
   * Returns the number of remaining bytes that can be read (or skipped over)
   * from this input stream.
   * <p>
   * The value returned is <code>count&nbsp;- pos</code>,
   * which is the number of bytes remaining to be read from the input buffer.
   *
   * @return  the number of remaining bytes that can be read (or skipped
   *          over) from this input stream without blocking.
   */
  override def available(): Int = count - pos;

  /**
   * Tests if this <code>InputStream</code> supports mark/reset. The
   * <code>markSupported</code> method of <code>ByteArrayInputStream</code>
   * always returns <code>true</code>.
   *
   * @since   JDK1.1
   */
  override def markSupported(): Boolean = false

  /**
   * Resets the buffer to the marked position.  The marked position
   * is 0 unless another position was marked or an offset was specified
   * in the constructor.
   */
  override def reset() {
    pos = 0
    rem = arrays
    cursor = 0
  }

}
