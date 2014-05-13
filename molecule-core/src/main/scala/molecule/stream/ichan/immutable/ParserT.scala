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
package stream
package ichan
package immutable

import parsing._

/**
 * Parse a ichan continuously
 *
 */
case class ParserT[A: Message, B: Message](
  val complexity: Int,
  val reset: Parser[A, B],
  val parser: Parser[A, B])
    extends Transformer[A, B] { outer =>

  final def apply(ichan: IChan[A]): IChan[B] = new IChan[B] {

    def complexity = ichan.complexity + outer.complexity

    def poison(signal: Signal) =
      ichan.poison(signal)

    def read(t: UThread, k: (Seg[B], IChan[B]) => Unit): Unit =
      read_(ichan, t, k)

    // invariant: ichan never nil
    final def read_(ichan: IChan[A],
      t: UThread,
      k: (Seg[B], IChan[B]) => Unit): Unit =
      ichan.read(t,
        { (seg, next) =>
          next match {
            case IChan(signal) =>
              val (bs, failure) = parseFinish(reset, parser, seg, signal)
              failure match {
                case Some(fail) =>
                  k(bs, IChan.empty[B](fail))
                case None =>
                  k(bs, IChan.empty(signal))
              }
            case _ =>
              if (seg.isEmpty)
                read_(next, t, k) //skip nil segments => loop
              else
                parse(parser, seg, next, t, k)
          }
        }
      )

    // invariant: tail never nil 
    private[this] final def parse(parser: Parser[A, B], as: Seg[A], tail: IChan[A], t: UThread, k: (Seg[B], IChan[B]) => Unit): Unit = {
      val (bs, result) = parsePartial(reset, parser, as)
      result match {
        case None =>
          k(bs, tail.add(new ParserT(complexity, reset, reset)))
        case Some(Right(parser)) =>
          k(bs, tail.add(new ParserT(complexity, reset, parser)))
        case Some(Left((fail, rem))) =>
          rem.poison(fail)
          tail.poison(fail)
          k(bs, IChan.empty(fail))
      }
    }

    def add[C: Message](transformer: Transformer[B, C]): IChan[C] = {
      type P = C => Boolean
      transformer match {
        case MapperT(complexity, f) =>
          ParserT[A, C](outer.complexity + complexity,
            reset.map(f),
            parser.map(f)).apply(ichan)
        case PartialMapperT(complexity, pf) =>
          ParserT[A, C](outer.complexity + complexity,
            reset.collect(pf),
            parser.collect(pf)).apply(ichan)
        case FilterT(complexity, p: P) =>
          ParserT[A, C](outer.complexity + complexity,
            reset.asInstanceOf[Parser[A, C]].filter(p),
            parser.asInstanceOf[Parser[A, C]].filter(p)).apply(ichan)
        case _ =>
          transformer(this)
      }
    }

  }
}
