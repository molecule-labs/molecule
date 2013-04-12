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

class DebugT[A: Message](f: Either[Signal, A] => Unit) extends Transformer[A, A] { outer =>

  def apply(ochan: OChan[A]): OChan[A] = new OChan[A] {

    def write(t: UThread, seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit): Unit = {
      seg.foreach { a => f(Right(a)) }
      sigOpt.foreach { s => f(Left(s)) }
      ochan.write(t, seg, sigOpt, ochan => k(ochan.add(outer)))
    }

    def close(signal: Signal): Unit = {
      f(Left(signal))
      ochan.close(signal)
    }

    def add[B: Message](t: Transformer[A, B]): OChan[B] =
      t(this)

  }
}
