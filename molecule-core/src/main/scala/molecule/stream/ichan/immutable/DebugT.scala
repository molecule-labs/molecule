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
class DebugT[A: Message](f: Either[Signal, A] => Unit) extends Transformer[A, A] {
  outer =>

  def apply(ichan: IChan[A]) = new IChan[A] {

    def complexity = ichan.complexity

    def read(t: UThread,
      k: (Seg[A], IChan[A]) => Unit) =
      ichan.read(t,
        { (seg, next) =>
          seg.foreach(a => f(Right(a)))
          next match {
            case NilIChan(signal) => f(Left(signal))
            case _ => ()
          }
          k(seg, next.add(outer))
        }
      )

    def poison(signal: Signal) = {
      f(Left(signal))
      ichan.poison(signal)
    }

    def add[B: Message](t: Transformer[A, B]): IChan[B] =
      t(this)
  }

}
