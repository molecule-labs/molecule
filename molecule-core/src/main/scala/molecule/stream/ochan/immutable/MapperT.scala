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

case class MapperT[A, B: Message](complexity: Int, f: B => A) extends Transformer[A, B] { outer =>

  def apply(ochan: OChan[A]): OChan[B] = new OChan[B] {

    def write(t: UThread, seg: Seg[B], sigOpt: Option[Signal], k: OChan[B] => Unit): Unit =
      ochan.write(t, seg.map(f), sigOpt, ochan => k(ochan.add(outer)))

    def close(signal: Signal): Unit =
      ochan.close(signal)

    def add[C: Message](transformer: Transformer[B, C]): OChan[C] =
      transformer match {
        case MapperT(c, e) =>
          new MapperT[A, C](complexity + c, e andThen f).apply(ochan)
        case FlatMapperT(c, e) =>
          new FlatMapperT[A, C](complexity + c, e andThen (_.map(f))).apply(ochan)
        case pars: ParserT[_, _] => // p:ParserT[B, C] => unchecked warning
          val p = pars.asInstanceOf[ParserT[B, C]]
          ParserT[A, C](outer.complexity + p.complexity,
            p.reset.map(f),
            p.parser.map(f)).apply(ochan)
        case _ =>
          transformer(this)
      }
  }
}

case class PartialMapperT[A, B: Message](val complexity: Int, f: PartialFunction[B, A])
    extends Transformer[A, B] { outer =>

  def apply(ochan: OChan[A]): OChan[B] = new OChan[B] {

    def write(t: UThread, seg: Seg[B], sigOpt: Option[Signal], k: OChan[B] => Unit): Unit =
      ochan.write(t, seg.map(f), sigOpt, ochan => k(ochan.add(outer)))

    def close(signal: Signal): Unit =
      ochan.close(signal)

    def add[C: Message](transformer: Transformer[B, C]): OChan[C] =
      transformer match {
        case pars: ParserT[_, _] => // pars:ParserT[B, C] => unchecked warning
          val p = pars.asInstanceOf[ParserT[B, C]]
          ParserT[A, C](outer.complexity + p.complexity,
            p.reset.collect(f),
            p.parser.collect(f)).apply(ochan)
        case _ =>
          transformer(this)
      }
  }
}
