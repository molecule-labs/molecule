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

import channel.RIChan
import stream.{ IChan, OChan }

/**
 * Trait that represent the free connections of a process,
 * which still need to be connected.
 *
 * Note on conventions: the input of a process becomes an output
 * for another process and inversely.
 */
sealed trait Connections[R] {
  val rchan: RIChan[R]
}

case class Connections0x0[R](rchan: RIChan[R]) extends Connections[R]
case class Connections1x0[O1, R](ichan1: OChan[O1], rchan: RIChan[R]) extends Connections[R]
case class Connections0x1[I1, R](ochan1: IChan[I1], rchan: RIChan[R]) extends Connections[R]
case class Connections1x1[O1, I1, R](ichan1: OChan[O1], ochan1: IChan[I1], rchan: RIChan[R]) extends Connections[R]
case class Connections2x0[O1, O2, R](ichan1: OChan[O1], ichan2: OChan[O2], rchan: RIChan[R]) extends Connections[R]
case class Connections0x2[I1, I2, R](ochan1: IChan[I1], ochan2: IChan[I2], rchan: RIChan[R]) extends Connections[R]
case class Connections2x1[O1, O2, I1, R](ichan1: OChan[O1], ichan2: OChan[O2], ochan1: IChan[I1], rchan: RIChan[R]) extends Connections[R]
case class Connections1x2[O1, I1, I2, R](ichan1: OChan[O1], ochan1: IChan[I1], ochan2: IChan[I2], rchan: RIChan[R]) extends Connections[R]
case class Connections2x2[O1, O2, I1, I2, R](ichan1: OChan[O1], ichan2: OChan[O2], ochan1: IChan[I1], ochan2: IChan[I2], rchan: RIChan[R]) extends Connections[R]
case class Connections3x0[O1, O2, O3, R](ichan1: OChan[O1], ichan2: OChan[O2], ichan3: OChan[O3], rchan: RIChan[R]) extends Connections[R]
case class Connections0x3[I1, I2, I3, R](ochan1: IChan[I1], ochan2: IChan[I2], ochan3: IChan[I3], rchan: RIChan[R]) extends Connections[R]
case class Connections3x1[O1, O2, O3, I1, R](ichan1: OChan[O1], ichan2: OChan[O2], ichan3: OChan[O3], ochan1: IChan[I1], rchan: RIChan[R]) extends Connections[R]
case class Connections1x3[O1, I1, I2, I3, R](ichan1: OChan[O1], ochan1: IChan[I1], ochan2: IChan[I2], ochan3: IChan[I3], rchan: RIChan[R]) extends Connections[R]
case class Connections3x2[O1, O2, O3, I1, I2, R](ichan1: OChan[O1], ichan2: OChan[O2], ichan3: OChan[O3], ochan1: IChan[I1], ochan2: IChan[I2], rchan: RIChan[R]) extends Connections[R]
case class Connections2x3[O1, O2, I1, I2, I3, R](ichan1: OChan[O1], ichan2: OChan[O2], ochan1: IChan[I1], ochan2: IChan[I2], ochan3: IChan[I3], rchan: RIChan[R]) extends Connections[R]
case class Connections3x3[O1, O2, O3, I1, I2, I3, R](ichan1: OChan[O1], ichan2: OChan[O2], ichan3: OChan[O3], ochan1: IChan[I1], ochan2: IChan[I2], ochan3: IChan[I3], rchan: RIChan[R]) extends Connections[R]
