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
 * Misc utilities
 */
package object utils {
  val NOOP0 = () => ()
  val NOOP = { a: Any => () }
  val NOOP2 = (a: Any, b: Any) => ()

  /**
   * Platform specific line separator
   */
  val lineSep = System.getProperty("line.separator");

  /**
   * We keep a random number generator per env because this is much more
   * efficient.
   */
  import java.util.Random
  private[this] lazy val rand = new ThreadLocal[Random] {
    override def initialValue(): Random = {
      new Random()
    }
  }

  final def random(): Random = {
    rand.get
  }

  import java.nio.{ ByteBuffer, CharBuffer }
  import java.nio.charset.Charset
  import java.nio.charset.{ CharsetDecoder, CharsetEncoder }

  def decode(charset: String): ByteBuffer => CharBuffer = {
    val decoder: CharsetDecoder = Charset.forName(charset).newDecoder
    decoder.decode
  }

  def encode(charset: String): CharBuffer => ByteBuffer = {
    val encoder: CharsetEncoder = Charset.forName(charset).newEncoder
    encoder.encode
  }

}
