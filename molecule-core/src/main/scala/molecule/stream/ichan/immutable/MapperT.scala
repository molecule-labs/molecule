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

/**
 * Apply a function to elements of a stream
 *
 */
case class MapperT[A, B: Message](val complexity: Int, f: A => B) extends StatelessT[A, B] {
  outer =>

  def transform(seg: Seg[A]): Seg[B] =
    seg.map(f)

  def add[C: Message](orig: IChan[A], self: IChan[B], t: Transformer[B, C]): IChan[C] =
    t match {
      case MapperT(c, g) =>
        new MapperT[A, C](c + complexity, g.compose(f)).apply(orig)
      case StateT(d, z, fsm) =>
        StateT[A, Any, C](complexity + d, z, (s, a) => fsm(s, f(a))).apply(orig)
      case _ =>
        t(self)
    }

}

case class SegMapperT[A, B: Message](val complexity: Int, f: Seg[A] => Seg[B])
    extends StatelessT[A, B] { outer =>

  def transform(seg: Seg[A]): Seg[B] =
    f(seg)

  def add[C: Message](orig: IChan[A], self: IChan[B], t: Transformer[B, C]): IChan[C] =
    t match {
      case SegMapperT(complexity, g) =>
        SegMapperT[A, C](complexity + outer.complexity, g.compose(f)).apply(orig)
      case _ =>
        t(self)
    }

}

case class PartialMapperT[A: Message, B: Message](val complexity: Int, f: PartialFunction[A, B])
    extends StatelessT[A, B] { outer =>

  def transform(seg: Seg[A]): Seg[B] =
    seg.collect(f)

  def add[C: Message](orig: IChan[A], self: IChan[B], t: Transformer[B, C]): IChan[C] =
    t(self)
}
