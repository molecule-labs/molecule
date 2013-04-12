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
package ochan
package immutable

import parsing._

/**
 * Parse a ichan continuously
 *
 */
case class ParserT[A, B: Message](
  val complexity: Int,
  val reset: Parser[B, A],
  val parser: Parser[B, A])
    extends Transformer[A, B] { outer =>

  final def apply(ochan: OChan[A]): OChan[B] = new OChan[B] {

    def close(signal: Signal) =
      ochan.close(signal)

    def write(t: UThread, seg: Seg[B], sigOpt: Option[Signal], k: OChan[B] => Unit): Unit = {
      sigOpt match {
        case sig @ Some(signal) =>
          val (bs, failure) = parseFinish(reset, parser, seg, signal)
          failure match {
            case sig @ Some(fail) =>
              ochan.write(t, bs, sig, utils.NOOP)
              k(OChan(fail))
            case _ =>
              ochan.write(t, bs, sig, utils.NOOP)
              k(OChan(signal))
          }
        case _ =>
          if (seg.isEmpty)
            k(this)
          else
            parse(parser, seg, t, k)
      }
    }

    // invariant: tail never nil 
    private[this] final def parse(parser: Parser[B, A], bs: Seg[B], t: UThread, k: OChan[B] => Unit): Unit = {
      val (as, result) = parsePartial(reset, parser, bs)
      result match {
        case None =>
          ochan.write(t, as, None, ochan => k(ochan.add(new ParserT(complexity, reset, reset))))
        case Some(Right(parser)) =>
          ochan.write(t, as, None, ochan => k(ochan.add(new ParserT(complexity, reset, parser))))
        case Some(Left((fail, rem))) =>
          rem.poison(fail)
          ochan.close(fail)
          k(OChan(fail))
      }
    }

    def add[C: Message](transformer: Transformer[B, C]): OChan[C] =
      transformer(this)

  }
}
