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

/**
 * First-class channel that reschedules the continuation of a system-level
 * channel inside its user-level thread.
 *
 */
class BackOChan[A: Message] private (private[molecule] final val ochan: channel.OChan[A]) extends OChan[A] {

  def write(t: UThread, seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit): Unit =
    ochan.write(seg, sigOpt, next => t.submit(k(BackOChan(next))))

  def close(signal: Signal): Unit =
    ochan.close(signal)

  def add[B: Message](transformer: Transformer[A, B]): OChan[B] =
    transformer(this)

}

object BackOChan {

  def apply[A: Message](ochan: channel.OChan[A]): OChan[A] =
    // hot spot optimisation
    if (ochan.isInstanceOf[channel.OChan.Nil[_]])
      OChan(ochan.asInstanceOf[channel.OChan.Nil[_]].signal)
    else
      new BackOChan(ochan)
  //   ochan match {
  //      case channel.OChan(signal) => NilOChan(signal)
  //      case _ => new BackOChan(ochan)
  //    }

}
