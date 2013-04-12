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
package parsers.ascii

import parsers.utils._
import java.nio.ByteBuffer

/**
 * An immutable class that creates a String by prepending character buffers
 *
 */
final class StringBuilder private (racc: ReverseAccumulator[ByteBuffer]) {
  @inline
  final def length = racc.length

  @inline
  final def prepend(cb: ByteBuffer): StringBuilder =
    new StringBuilder(racc.prepend(cb, cb.remaining))

  @inline
  final def result =
    racc.result(StringBuilder.mutableStringBuilder)
}

object StringBuilder {

  def apply() = new StringBuilder(ReverseAccumulator[ByteBuffer])

  private val mutableStringBuilder: Int => MPrependBuilder[ByteBuffer, String] = { length =>
    new MPrependBuilder[ByteBuffer, String] {
      val buffer = new Array[Byte](length)
      var limit = length

      def prepend(elem: ByteBuffer): MPrependBuilder[ByteBuffer, String] = {
        val len = elem.remaining
        val pos = limit - len
        System.arraycopy(elem.array(), elem.position, buffer, pos, len)
        limit = pos
        this
      }

      def result: String = {
        val cb = ASCII.charset.decode(ByteBuffer.wrap(buffer))
        newString(cb.position, cb.remaining, cb.array())
      }
    }
  }

}