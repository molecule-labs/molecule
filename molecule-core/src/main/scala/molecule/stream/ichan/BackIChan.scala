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

/**
 * First-class channel that reschedules the continuation of
 * a system-level channel inside its user-level thread.
 *
 */
final class BackIChan[A] private (private[molecule] final val ichan: channel.IChan[A]) extends IChan[A] {
  def complexity = 1

  def read(t: UThread,
    k: (Seg[A], IChan[A]) => Unit): Unit =
    ichan.read((seg, next) => t.submit(
      k(seg, BackIChan(next))
    ))

  def poison(signal: Signal): Unit =
    ichan.poison(signal)

  def add[B: Message](transformer: Transformer[A, B]): IChan[B] =
    transformer(this)
}

object BackIChan {
  def apply[A](ichan: channel.IChan[A]): IChan[A] =
    // hot spot optimisation
    if (ichan.isInstanceOf[channel.IChan.Nil])
      IChan.empty(ichan.asInstanceOf[channel.IChan.Nil].signal)
    else
      new BackIChan(ichan)
  //    ichan match {
  //      case channel.IChan(s) => NilIChan(s)
  //      case c => new BackIChan(c)
  //    }

}
