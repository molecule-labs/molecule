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

private[molecule] object Foreach extends ProcessType {

  override def name = "Foreach"

  def apply[A, B](f: A => B)(ichan: IChan[A]): Process[Unit] = new CoreProcess[Unit] {

    def ptype = Foreach

    def main(t: UThread, rchan: ROChan[Unit]): Unit = {
      def _foreach(ich: IChan[A]): Unit = {
        ich.read(t, { (seg, ich) =>
          seg.foreach(f)
          ich match {
            case IChan(EOS) => rchan.success_!(())
            case IChan(signal) => rchan.failure_!(signal)
            case _ => _foreach(ich)
          }
        })
      }
      _foreach(ichan)
    }

  }
}
