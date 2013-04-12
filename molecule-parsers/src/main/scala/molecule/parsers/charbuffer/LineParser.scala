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
package parsers.charbuffer

import molecule.parsing._
import java.nio.CharBuffer
import parsers.utils.{ ReverseAccumulator }

/**
 * Assemble a line from a stream of character buffers.
 * A line is a sequence of characters ending with either a carriage-return character ('\r') or a newline
 * character ('\n'). In addition, a carriage-return character followed immediately by a newline character is * treated as a single end-of-line
 * token.
 *
 */
final private class LineParser private[charbuffer] (builder: StringBuilder, maxLength: Int, crFound: Boolean) extends Parser[CharBuffer, String] {

  def reset = LineParser(maxLength)

  def name = "charbuffer.line"

  def result(signal: Signal) =
    if (builder.length > 0)
      Some(Right(Done(builder.result, NilSeg)))
    else
      None

  def apply(seg: Seg[CharBuffer]): Res = {
    if (seg.isEmpty)
      Partial(new LineParser(builder, maxLength, false))
    else
      scan(seg.head, seg.tail, builder)
  }

  /**
   * pos indexes position of '\n' or '\r' in current buffer: cb
   */
  private def splitAt(pos: Int, cb: CharBuffer, cbs: Seg[CharBuffer]): (CharBuffer, Seg[CharBuffer]) = {
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
   * Accumulate charbuffer's until the end of line is detected
   */
  @scala.annotation.tailrec
  private[this] final def scan(cb: CharBuffer, cbs: Seg[CharBuffer], builder: StringBuilder): Res = {
    var i = cb.position
    while (i < cb.limit) {
      val c = cb.get(i)
      if (c == '\r') {
        val (head, tail) = splitAt(i, cb, cbs)
        if (head.remaining + builder.length > maxLength)
          return parseError(head, builder)

        if (tail.isEmpty)
          return Partial(new LineParser(builder.append(head), maxLength, true))
        else
          return finishLF(tail.head, tail.tail, builder.append(head))
      } else if (c == '\n') {
        val (head, tail) = splitAt(i, cb, cbs)
        if (head.remaining + builder.length > maxLength)
          return parseError(head, builder)
        return Done(builder.append(head).result, tail)
      } else
        i += 1
    }

    if (cb.remaining + builder.length > maxLength)
      return parseError(cb, builder)

    val nbuilder = builder.append(cb)
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
  private[this] final def finishLF(cb: CharBuffer, cbs: Seg[CharBuffer], builder: StringBuilder): Res =
    if (cb.remaining == 0) {
      if (cbs.isEmpty)
        Partial(new LineParser(builder, maxLength, true))
      else
        finishLF(cbs.head, cbs.tail, builder)
    } else {
      val c = cb.get(cb.position)
      if (c == '\n') { // swallow '\n'
        val (_, tail) = splitAt(cb.position, cb, cbs)
        Done(builder.result, tail)
      } else // line end with '\r'
        Done(builder.result, cb +: cbs)
    }

  private[this] final def parseError(cb: CharBuffer, builder: StringBuilder): Res =
    Fail(name, "Maximum line length exceeded (" + (cb.remaining + builder.length) + " > " + maxLength + "):" + builder.append(cb).result)

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