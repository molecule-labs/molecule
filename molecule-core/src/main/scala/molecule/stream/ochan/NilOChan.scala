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

class NilOChan[A: Message] private (val signal: Signal) extends OChan[A] {

  def write(thread: UThread, seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit): Unit = {
    seg.poison(signal)
    throw new Error("Cannot write on NilChan:" + signal)
  }

  def close(signal: Signal) = {}

  def add[B: Message](transformer: ochan.Transformer[A, B]): OChan[B] =
    transformer match {
      case b: immutable.BufferT[_] =>
        transformer(this)
      case _ =>
        new NilOChan(signal)
    }
}

object NilOChan {

  def apply[A: Message](signal: Signal): NilOChan[A] =
    new NilOChan(signal)

  def unapply(nilochan: NilOChan[_]): Option[Signal] = Some(nilochan.signal)

}
