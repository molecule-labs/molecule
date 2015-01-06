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

import molecule.parsing._
import java.nio.ByteBuffer
import parsers.utils.{ ReverseAccumulator }
import ASCII.{ LF, CR }

/**
 * Assemble a line from a stream of character buffers.
 * A line is a sequence of characters ending with either a carriage-return character ('\r') or a newline
 * character ('\n'). In addition, a carriage-return character followed immediately by a newline character is * treated as a single end-of-line token.
 *
 */
final private class LineParser private (builder: StringBuilder, maxLength: Int, crFound: Boolean) extends Parser[ByteBuffer, String] {
  type Res = ParseResult[String, ByteBuffer]

  def reset = LineParser(maxLength)

  def name = "ascii.line"

  def apply(seg: Seg[ByteBuffer]): Res = {
    if (seg.isEmpty)
      Partial(new LineParser(builder, maxLength, false))
    else
      scan(seg.head, seg.tail, builder)
  }

  def result(signal: Signal) =
    if (builder.length > 0)
      Some(Right(Done(builder.result, NilSeg)))
    else
      None

  /**
   * pos indexes position of '\n' or '\r' in current buffer: cb
   */
  private def splitAt(pos: Int, cb: ByteBuffer, cbs: Seg[ByteBuffer]): (ByteBuffer, Seg[ByteBuffer]) = {
    val head = cb.duplicate
    head.limit(pos)
    val tail = {
      val npos = pos + 1 // skip '\r' or '\n' character
      if (npos < cb.limit) { // npos indexes a character of cb
        val tmp = cb.duplicate
        tmp.position(npos)
        tmp +: cbs
      } else { // npos indexes a character in cbs
        cbs
      }
    }
    (head, tail)
  }

  /**
   * Accumulate ByteBuffer's until the end of line is detected
   */
  @scala.annotation.tailrec
  private[this] final def scan(cb: ByteBuffer, cbs: Seg[ByteBuffer], builder: StringBuilder): Res = {
    var i = cb.position
    while (i < cb.limit) {
      val b = cb.get(i)
      if (b == LF) {
        val (head, tail) = splitAt(i, cb, cbs)
        if (head.remaining + builder.length > maxLength)
          return parseError(head, builder)

        if (tail.isEmpty)
          return Partial(new LineParser(builder.prepend(head), maxLength, true))
        else
          return finishLF(tail.head, tail.tail, builder.prepend(head))
      } else if (b == CR) {
        val (head, tail) = splitAt(i, cb, cbs)
        if (head.remaining + builder.length > maxLength)
          return parseError(head, builder)
        return Done(builder.prepend(head).result, tail)
      } else
        i += 1
    }

    if (cb.remaining + builder.length > maxLength)
      return parseError(cb, builder)

    val nbuilder = builder.prepend(cb)
    if (cbs.isEmpty)
      Partial(new LineParser(nbuilder, maxLength, false))
    else
      scan(cbs.head, cbs.tail, nbuilder)
  }

  /**
   * Last character found was '\r'
   * cb.get(cb.position) is either '\n' or another character.
   */
  @scala.annotation.tailrec
  private[this] final def finishLF(cb: ByteBuffer, cbs: Seg[ByteBuffer], builder: StringBuilder): Res =
    if (cb.remaining == 0) {
      if (cbs.isEmpty)
        Partial(new LineParser(builder, maxLength, true))
      else
        finishLF(cbs.head, cbs.tail, builder)
    } else {
      val b = cb.get(cb.position)
      if (b == CR) { // swallow '\n'
        /* @author Koen Daenen
		 * BUG KD 2012-09-24 (same bug as KD 2012-05-30 found earlier in charbuffer parser)
         * val (_, tail) = splitAt(cb.position + 1, cb, cbs)
		 */
        val (_, tail) = splitAt(cb.position, cb, cbs)
        Done(builder.result, tail)
      } else // line end with '\r'
        Done(builder.result, cb +: cbs)
    }

  private[this] final def parseError(cb: ByteBuffer, builder: StringBuilder): Res =
    Fail(name, "Maximum line length exceeded (" + (cb.remaining + builder.length) + " > " + maxLength + "):" + builder.prepend(cb).result)

}

private object LineParser {
  /**
   * Create a line parser.
   * A line is a sequence of characters ending with either a carriage-return character ('\r') or a newline
   * character ('\n'). In addition, a carriage-return character followed immediately by a newline character is * treated as a single end-of-line token.
   *
   * @param maxLength The maximum length of the line accepted by this parser.
   *
   */
  def apply(maxLength: Int): LineParser = new LineParser(StringBuilder(), maxLength, false)
}