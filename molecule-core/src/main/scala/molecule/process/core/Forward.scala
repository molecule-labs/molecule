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

private[molecule] object Forward extends ProcessType {

  override def name = "Forwarder"

  type R[A] = (IChan[A], OChan[A])

  def apply[A](ichan: IChan[A], ochan: OChan[A]): Process[R[A]] = new CoreProcess[R[A]] {

    def ptype = Forward

    def main(t: UThread, rchan: ROChan[R[A]]): Unit = {

      def loop(ichan: IChan[A], ochan: OChan[A]) {
        ichan.read(t,
          { (seg, nichan) =>
            ochan.write(t, seg, None, {
              case nochan: stream.ochan.NilOChan[_] =>
                rchan.success_!(nichan, nochan)
              case nochan =>
                nichan match {
                  case IChan(signal) =>
                    rchan.success_!(nichan, nochan)
                  case nichan =>
                    loop(nichan, nochan)
                }
            })
          })
      }

      ichan match {
        case IChan(signal) =>
          rchan.success_!(ichan, ochan)
        case _ =>
          ochan match {
            case OChan(signal) =>
              rchan.success_!(ichan, ochan)
            case _ =>
              loop(ichan, ochan)
          }
      }
    }
  }

}