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
package process
package core

import module._

private[molecule] object Fold extends ProcessType {

  override def name = "Fold"

  def apply[A, B: Message](z: B)(f: (B, A) => B)(ichan: IChan[A]): Process[B] = new CoreProcess[B] {

    def ptype = Fold

    def main(t: UThread, rchan: ROChan[B]): Unit = {
      def loop(ichan: IChan[A], acc: B): Unit = {
        ichan match {
          case IChan(EOS) => rchan.success_!(acc)
          case IChan(signal) => Message[B].poison(acc, signal); rchan.failure_!(signal)
          case _ => ichan.read(t, { (seg, ichan) => loop(ichan, seg.foldLeft(acc)(f)) })
        }
      }
      loop(ichan, z)
    }

  }
}