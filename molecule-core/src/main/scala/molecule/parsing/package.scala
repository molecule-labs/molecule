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
 * Parsing utility methods
 */
package object parsing {

  /**
   * Apply a parser until it fails or there is no more data.
   *
   * @param reset the parser in an initial state
   * @param parser the parser in a subsequent state
   * @param as the segment to parse
   * @return the resulting messages and, optionally, either the segment that could not be
   * consumed because of a failure or a partial result because more data is required. None
   * means that all the segment has been parsed successfully and that no intermediate state
   * is left.
   */
  def parsePartial[A, B](reset: Parser[A, B], parser: Parser[A, B], as: Seg[A]): (Seg[B], Option[Either[(Fail, Seg[A]), Parser[A, B]]]) =
    _parseAsMuchAsUCan0(reset, parser, as)

  private[this] final def _parseAsMuchAsUCan0[A, B](reset: Parser[A, B], parser: Parser[A, B], as: Seg[A]): (Seg[B], Option[Either[(Fail, Seg[A]), Parser[A, B]]]) =
    if (as.isEmpty)
      (NilSeg, None)
    else
      parser(as) match {
        case Done(b, as) =>
          if (as.isEmpty)
            (Seg(b), None)
          else {
            _parseAsMuchAsUCan1(reset, as, Seg(b), as)
          }
        case Partial(parser) =>
          (NilSeg, Some(Right(parser)))
        case fail: Fail =>
          (NilSeg, Some(Left((fail, as))))
      }

  @scala.annotation.tailrec
  private[this] final def _parseAsMuchAsUCan1[A, B](parser: Parser[A, B], as: Seg[A], presult: Seg[B], trail: Seg[A]): (Seg[B], Option[Either[(Fail, Seg[A]), Parser[A, B]]]) = {
    parser(as) match {
      case Done(a, rem) =>
        val result = presult :+ a
        if (rem.isEmpty)
          (result, None)
        else
          _parseAsMuchAsUCan1(parser, rem, result, trail ++ as)
      case Partial(parser) =>
        (presult, Some(Right(parser)))
      case fail: Fail =>
        (presult, Some(Left((fail, trail ++ as))))
    }
  }

  /**
   * Parse all the messages in the last segment received on a channel.
   *
   *  @param reset the parser in an initial state
   *  @param parser the parser in a subsequent state
   *  @param as the last segment
   *  @param signal the signal accompanying the last segment
   *  @return a segment of the last successfully parsed messages
   *  and an option indicating whether the last parse failed or not.
   */
  def parseFinish[A, B](reset: Parser[A, B], parser: Parser[A, B], as: Seg[A], signal: Signal): (Seg[B], Option[Fail]) = {
    val (seg, opt) = parseProbe(reset, parser, as, signal)
    (seg, opt.map(_.fold(identity, _._1)))
  }

  /**
   * Parse all the messages in the last segment received on a channel.
   *
   *  @param reset the parser in an initial state
   *  @param parser the parser in a subsequent state
   *  @param as the last segment
   *  @param signal the signal accompanying the last segment
   *  @return a segment of the last successfully parsed messages
   *  and an option indicating whether the last parse failed or not. In case there is a failure
   *  and it is recoverable, the last segment that lead to the failure is returned with it.
   */
  def parseProbe[A, B](reset: Parser[A, B], parser: Parser[A, B], as: Seg[A], signal: Signal): (Seg[B], Option[Either[Fail, (Fail, Seg[A])]]) = {
    val (bs, res) = parsePartial(reset, parser, as)
    res match {
      case None =>
        (bs, None)
      case Some(Right(parser)) =>
        parser.result(NilSeg, signal) match {
          case None =>
            (bs, None)
          case Some(Right(Done(b, _))) =>
            (b +: bs, None)
          case Some(Left(fail)) =>
            (bs, Some(Left(fail)))
        }
      case Some(Left((fail, rem))) =>
        (bs, Some(Right((fail, rem))))
    }
  }

}
