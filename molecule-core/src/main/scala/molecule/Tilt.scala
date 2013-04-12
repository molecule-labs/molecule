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
 * Pair of values returned by operators that sequence two monadic actions
 * (see IO and parser combinators).
 */
case class ~[+a, +b](_1: a, _2: b) {
  override def toString = "(" + _1 + "~" + _2 + ")"
}

/**
 * Convenience functions to flatten pair of values created recursively by sequencing
 *  monadic operations. (See '~' )
 */
trait Tilt {
  implicit def flatten2[A, B, C](f: (A, B) => C) =
    (p: ~[A, B]) => p match { case a ~ b => f(a, b) }
  implicit def flatten3[A, B, C, D](f: (A, B, C) => D) =
    (p: ~[~[A, B], C]) => p match { case a ~ b ~ c => f(a, b, c) }
  implicit def flatten4[A, B, C, D, E](f: (A, B, C, D) => E) =
    (p: ~[~[~[A, B], C], D]) => p match { case a ~ b ~ c ~ d => f(a, b, c, d) }
  implicit def flatten5[A, B, C, D, E, F](f: (A, B, C, D, E) => F) =
    (p: ~[~[~[~[A, B], C], D], E]) => p match { case a ~ b ~ c ~ d ~ e => f(a, b, c, d, e) }
  implicit def headOptionTailToFunList[A, T](f: List[A] => T) =
    (p: ~[A, Option[List[A]]]) => f(p._1 :: (p._2 match { case Some(xs) => xs case None => Nil }))
}