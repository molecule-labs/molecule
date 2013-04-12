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

import parsing._
import java.nio.CharBuffer

trait RegexParsers extends Parsers[CharBuffer] {

  import scala.util.matching.Regex

  protected val whiteSpace = """\s+""".r

  def skipWhitespace = whiteSpace.toString.length > 0

  protected def handleWhiteSpace(source: Seg[CharBuffer]): Seg[CharBuffer] = {
    if (skipWhitespace) {
      @scala.annotation.tailrec
      def loop(src: Seg[CharBuffer]): Seg[CharBuffer] = {
        if (src.isEmpty) {
          src
        } else {
          val (h, t) = src.pop
          val cb = h.value
          if (cb.remaining == 0)
            loop(t)
          else
            (whiteSpace findPrefixMatchOf (cb)) match {
              case Some(matched) =>
                if (matched.end == cb.remaining())
                  loop(t)
                else {
                  val rem = cb.duplicate
                  rem.position(cb.position + matched.end)
                  rem +: t
                }
              case None => src
            }
        }
      }

      loop(source)

    } else {
      var src = source
      while (!src.isEmpty && src.head.remaining == 0)
        src = source.tail
      src
    }
  }

  /** A parser that matches a literal string */
  implicit def literal(s: String): Parser[CharBuffer, String] = new LitteralParser(0, s, List.empty, skipWhitespace)

  class LitteralParser(idx: Int, s: String, acc: List[String], skipWhiteSpaces: Boolean) extends Parser[CharBuffer, String] {

    def name = "charbuffer.litteral"

    def reset: Parser[CharBuffer, String] = literal(s)

    final def apply(se: Seg[CharBuffer]): Res = {

      val seg = handleWhiteSpace(se)

      if (seg.isEmpty)
        return Partial(this)

      def loop(idx: Int, seg: Seg[CharBuffer], acc: List[String]): Res = {
        val (cb, cbs) = (seg.head, seg.tail)
        if (cb.remaining == 0)
          loop(idx, cbs, acc)
        else {
          var i = idx
          var j = 0
          while (i < s.length && j < cb.remaining && s.charAt(i) == cb.charAt(j)) {
            i += 1
            j += 1
          }
          if (i == s.length) { // found
            if (j == cb.remaining)
              Done(s, cbs)
            else {
              val ncb = cb.duplicate()
              ncb.position(ncb.position + j)
              Done(s, ncb +: cbs)
            }
          } else if (j == cb.remaining) { // partial match
            if (cbs.isEmpty)
              Partial(new LitteralParser(i, s, cb.toString +: acc, false))
            else
              loop(i, cbs, cb.toString +: acc)
          } else {
            StdErrors.mismatch(name, s, acc.reverse.mkString("") + cb)
          }
        }
      }

      loop(idx, seg, acc)
    }

    def result(signal: Signal) =
      Some(Left(StdErrors.prematureSignalMismatch(name, s, Seg(acc.reverse, acc.length), signal)))
  }

  /** A parser that matches a regex string */
  implicit def regex(r: Regex): Parser[CharBuffer, String] = new RegexParser(r, "", skipWhitespace)

  class RegexParser(r: Regex, found: String, skipWhitespaces: Boolean) extends Parser[CharBuffer, String] {

    def name = "charbuffer.regexparser"

    def apply(se: Seg[CharBuffer]): Res = {
      val seg = handleWhiteSpace(se)

      if (se.isEmpty)
        return Partial(this)

      val (cb, cbs) = se.pop

      val s = found + cb.value

      (r findPrefixMatchOf (s)) match {
        case Some(matched) =>
          val len = matched.end - found.length
          if (len == cb.value.remaining)
            Done(s.subSequence(0, matched.end).toString, cbs)
          else {
            val ncb = cb.value.duplicate()
            ncb.position(ncb.position + len)
            Done(s.subSequence(0, matched.end).toString, ncb +: cbs)
          }
        case None =>
          Partial(new RegexParser(r, s, skipWhitespace))
      }
    }

    def result(signal: Signal) =
      Some(Left(StdErrors.prematureSignalMismatch(name, r, Seg(found), signal)))

  }
}
