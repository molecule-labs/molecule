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

case class FilterT[A: Message](complexity: Int, p: A => Boolean) extends Transformer[A, A] { outer =>

  def apply(ochan: OChan[A]): OChan[A] = new OChan[A] {

    def write(t: UThread, seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit): Unit =
      ochan.write(t, seg.filter(p), sigOpt, ochan => k(ochan.add(outer)))

    def close(signal: Signal): Unit =
      ochan.close(signal)

    def add[B: Message](t: Transformer[A, B]): OChan[B] =
      (t: @unchecked) match {
        case f: FilterT[_] => // f:FilterT[A] => unchecked warning
          val filter = f.asInstanceOf[FilterT[A]]
          _add(filter)(ochan).asInstanceOf[OChan[B]]
        case pars: ParserT[_, _] => // pars:ParserT[A, B] => unchecked warning
          val parser = pars.asInstanceOf[ParserT[A, B]]
          ParserT[A, B](outer.complexity + parser.complexity,
            parser.reset.filter(p),
            parser.parser.filter(p)).apply(ochan)
        case _ =>
          t(this)
      }

    private[this] def _add(filter: FilterT[A]): FilterT[A] =
      new FilterT[A](filter.complexity + complexity,
        a => filter.p(a) && p(a))

  }
}
