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
 * Similar to list scan
 *
 */
object ScannerT {
  def apply[A, B: Message](z: B, op: (B, A) => B): Transformer[A, B] = new Transformer[A, B] {
    val fsm: (B, A) => (B, B) = { (b, a) =>
      val bn = op(b, a)
      (bn, bn)
    }
    def apply(ichan: IChan[A]): IChan[B] = {
      z +: ichan.add(StateT(1, z, fsm))
    }
  }
}
