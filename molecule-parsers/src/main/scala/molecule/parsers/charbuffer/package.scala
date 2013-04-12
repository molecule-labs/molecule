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
import java.nio.CharBuffer

/**
 * Character buffer parsing
 *
 */
package object charbuffer {

  /**
   * Parse a line.
   * A line is a sequence of characters ending with either a carriage-return character ('\r') or a newline
   * character ('\n'). In addition, a carriage-return character followed immediately by a newline character is * treated as a single end-of-line token.
   *
   * @param maxLength The maximum length of the line accepted by this parser.
   */
  def line(maxLength: Int): Parser[CharBuffer, String] = LineParser(maxLength)

  /**
   * Parser that accepts any character
   */
  final val anyChar: Parser[CharBuffer, Char] = new Parser[CharBuffer, Char] {

    def reset: Parser[CharBuffer, Char] = this

    def name = "charbuffer.anyChar"

    @scala.annotation.tailrec
    final def apply(seg: Seg[CharBuffer]): Res = {
      if (seg.isEmpty)
        return Partial(this)

      val (cb, cbs) = (seg.head, seg.tail)
      if (cb.remaining == 0)
        apply(cbs)
      else {
        if (cb.remaining > 1) {
          val rem = cb.duplicate()
          rem.position(cb.position + 1)
          Done(cb.get(0), rem +: cbs)
        } else
          Done(cb.get(0), cbs)
      }
    }

    def result(signal: Signal) =
      Some(Left(StdErrors.prematureSignal(name, NilSeg, signal)))
  }

  /**
   * Parser that matches a given character
   */
  implicit def char(c: Char): Parser[CharBuffer, Char] = new Parser[CharBuffer, Char] {

    def name = "charbuffer.char"

    @scala.annotation.tailrec
    final def apply(seg: Seg[CharBuffer]): Res = {
      if (seg.isEmpty)
        return Partial(this)

      val (cb, cbs) = (seg.head, seg.tail)
      if (cb.remaining == 0)
        apply(cbs)
      else if (cb.get(cb.position) != c)
        StdErrors.mismatch(name, c, cb.get(cb.position))
      else {
        if (cb.remaining > 1) {
          val rem = cb.duplicate()
          rem.position(cb.position + 1)
          Done(c, rem +: cbs)
        } else
          Done(c, cbs)
      }
    }

    def result(signal: Signal) =
      Some(Left(StdErrors.prematureSignal(name, NilSeg, signal)))
  }

  /**
   * Parser that accepts everything until byte b.
   * It fails if the first byte it receives is equal to byte b
   */
  def not(b: Char): Parser[CharBuffer, CharBuffer] = new Parser[CharBuffer, CharBuffer] {

    def name = "bytebuffer.not"

    @scala.annotation.tailrec
    final def apply(seg: Seg[CharBuffer]): Res = {
      if (seg.isEmpty)
        return Partial(this)

      val (bb, bbs) = (seg.head, seg.tail)
      if (bb.remaining == 0)
        apply(bbs)
      else {
        var i = bb.position
        if (bb.get(i) == b)
          StdErrors.mismatch(name, "!" + b, bb.get(i))
        else {
          i += 1
          val lim = bb.limit
          while (i < lim && bb.get(i) != b) {
            i += 1
          }
          val (bbb, bbbs) = splitAt(i, bb, bbs)
          Done(bbb, bbbs)
        }
      }
    }

    def result(signal: Signal) = None
  }

  private[charbuffer] def splitAt(pos: Int, bb: CharBuffer, bbs: Seg[CharBuffer]): (CharBuffer, Seg[CharBuffer]) = {
    if (bb.limit == pos) {
      (bb, bbs)
    } else {
      val part2 = bb.duplicate
      part2.position(pos)
      val part1 = bb.duplicate
      part1.limit(pos)
      (part1, part2 +: bbs)
    }
  }

}