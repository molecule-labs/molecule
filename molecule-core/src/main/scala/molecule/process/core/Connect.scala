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

private[molecule] final class Connect[A](
    @volatile private var ichan: IChan[A],
    @volatile private var ochan: OChan[A]) extends CoreProcess[Either[Signal, Signal]] {

  def ptype = Connect

  def main(t: UThread, rchan: ROChan[Either[Signal, Signal]]): Unit =
    Connect.connect(this, t, rchan, ichan, ochan, true)

}

private[molecule] object Connect extends ProcessType {

  override def name = "Connector"

  def apply[A](ichan: IChan[A], ochan: OChan[A]): Process[Either[Signal, Signal]] =
    new Connect(ichan, ochan)

  private final def connect[A](
    c: Connect[A],
    t: UThread, rchan: ROChan[Either[Signal, Signal]],
    ichan: IChan[A], ochan: OChan[A], init: Boolean): Unit = {

    if (init) {
      ichan match {
        case IChan(signal) =>
          ochan.close(signal)
          rchan.success_!(Left(signal))
          return
        case _ =>
      }

      c.ichan = null
      c.ochan = null
    }

    ochan match {
      case OChan(signal) =>
        ichan.poison(signal)
        rchan.success_!(Right(signal))
      case _ =>
        ichan.read(t, (seg, ichan) => ichan match {
          case IChan(signal) =>
            ochan.close(t, seg, signal)
            rchan.success_!(Left(signal))
          case _ =>
            ochan.write(t, seg, None, ochan => connect(c, t, rchan, ichan, ochan, false))
        })
    }
  }
}