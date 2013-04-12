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

class NilIChan private (val signal: Signal) extends IChan[Nothing] with TestableIChan[Nothing] {

  def complexity: Int = 0

  def read(t: UThread,
    k: (Seg[Nothing], IChan[Nothing]) => Unit) =
    throw new Error("Cannot read on NilIChan:" + signal)

  def poison(signal: Signal): Unit = {}

  def test(thread: UThread, ready: IChan[Nothing] => Unit) =
    ready(this)

  def add[B: Message](transformer: Transformer[Nothing, B]): IChan[B] =
    transformer match {
      case b: immutable.BufferT[_] => b(this)
      case _ => this
    }
}

object NilIChan {

  private[this] val eos = new NilIChan(EOS)

  def apply(signal: Signal): NilIChan = signal match {
    case EOS => eos
    case _ => new NilIChan(signal)
  }
  def apply(): NilIChan = eos

  def unapply(nilichan: NilIChan): Option[Signal] = Some(nilichan.signal)

}

