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
package parsers

import parsing._
import java.nio.ByteBuffer

/**
 * Character buffer parsers
 *
 * These parsers work for ASCII and ASCII compatible encodings, i.e.
 * (1) the byte representation of a character corresponds with the ASCII value, or
 * (2) the byte representation of a character is a single byte > 128, or
 * (3) the byte representation of a character is multiple bytes, all > 128 (cfr UTF-8 for non-ASCII characters)
 * As argument for these parsers though only pure ASCII characters should be used,
 * but any type may appear in the ByteBuffer that is being parsed.
 *
 */
package object ascii {

  /**
   * Parser that accepts any ASCII character
   */
  val anyChar: Parser[ByteBuffer, Char] = bytebuffer.anyByte ^^ { _.toChar }

  /**
   * Parse a line.
   * A line is a sequence of characters ending with either a carriage-return character ('\r') or a newline
   * character ('\n'). In addition, a carriage-return character followed immediately by a newline character is * treated as a single end-of-line token.
   *
   * @param maxLength The maximum length of the line accepted by this parser.
   */
  def line(maxLength: Int): Parser[ByteBuffer, String] = LineParser(maxLength)

  /**
   * Parser that succeeds if it matches a single ascii character
   */
  implicit def char(c: Char): Parser[ByteBuffer, Char] = bytebuffer.byte(c.toByte) ^^ { _.toChar }

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will build a ByteBuffer until byte c or any of bytes cs is found.
   */
  @inline
  final def bufferUntil(c: Char, cs: Char*): Parser[ByteBuffer, ByteBuffer] =
    bytebuffer.bufferUntil(c.toByte, cs.map(_.toByte): _*)

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will build a ByteBuffer until the received byte falls in one of the the specified ranges.
   */
  @inline
  final def bufferUntil(range: (Char, Char), ranges: (Char, Char)*): Parser[ByteBuffer, ByteBuffer] =
    bytebuffer.bufferUntil((range._1.toByte, range._2.toByte), ranges.map(r => (r._1.toByte, r._2.toByte)): _*)

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will build a ByteBuffer accepting all specified chars.
   */
  @inline
  final def buffer(char: Char, cs: Char*): Parser[ByteBuffer, ByteBuffer] =
    bytebuffer.buffer(char.toByte, cs.map(_.toByte): _*)

  /**
   * Parser from ByteBuffer to ByteBuffer.
   * This parser will build a ByteBuffer accepting all bytes that fall in the specified ranges.
   */
  @inline
  final def buffer(range: (Char, Char), ranges: (Char, Char)*): Parser[ByteBuffer, ByteBuffer] =
    bytebuffer.buffer((range._1.toByte, range._2.toByte), ranges.map(r => (r._1.toByte, r._2.toByte)): _*)

}