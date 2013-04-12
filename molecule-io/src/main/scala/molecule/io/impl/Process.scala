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
package io.impl

import molecule.{ process => proc }
import platform.UThread
import channel.ROChan
import io.IO
import stream.{ IChan, OChan }

/**
 * The base trait for all process instances.
 *
 */
private[io] trait Process[R] extends proc.Process[R] {

  def behavior: IO[R]

  def start(thread: UThread, rchan: ROChan[R]): Unit =
    new Activity(ptype, rchan).start(thread, behavior)

}

private[io] class Process0x0[R](processType: io.ProcessType0x0[R]) extends proc.Process0x0[R](processType) with Process[R] {
  def behavior = processType.main
}

private[io] class Process1x0[A: Message, R](processType: io.ProcessType1x0[A, R], ichan1: IChan[A]) extends proc.Process1x0[A, R](processType, ichan1) with Process[R] {
  def behavior =
    IO.open(1, ichan1) >>\ processType.main
}

private[io] class Process0x1[A: Message, R](processType: io.ProcessType0x1[A, R], ochan1: OChan[A]) extends proc.Process0x1[A, R](processType, ochan1) with Process[R] {
  def behavior =
    IO.open(1, ochan1) >>\ processType.main
}

private[io] class Process1x1[A: Message, B: Message, R](processType: io.ProcessType1x1[A, B, R], ichan1: IChan[A], ochan1: OChan[B]) extends proc.Process1x1[A, B, R](processType, ichan1, ochan1) with Process[R] {
  def behavior =
    IO.open(1, ichan1) ~ IO.open(1, ochan1) >>\ processType.main

}

private[io] class Process2x0[A: Message, B: Message, R](processType: io.ProcessType2x0[A, B, R], ichan1: IChan[A], ichan2: IChan[B]) extends proc.Process2x0[A, B, R](processType, ichan1, ichan2) with Process[R] {
  def behavior =
    IO.open(1, ichan1) ~ IO.open(2, ichan2) >>\ processType.main

}

private[io] class Process2x1[A: Message, B: Message, E: Message, R](processType: io.ProcessType2x1[A, B, E, R], ichan1: IChan[A], ichan2: IChan[B], ochan1: OChan[E]) extends proc.Process2x1[A, B, E, R](processType, ichan1, ichan2, ochan1) with Process[R] {
  def behavior =
    IO.open(1, ichan1) ~ IO.open(2, ichan2) ~ IO.open(1, ochan1) >>\ processType.main

}

private[io] class Process1x2[A: Message, E: Message, F: Message, R](processType: io.ProcessType1x2[A, E, F, R], ichan1: IChan[A], ochan1: OChan[E], ochan2: OChan[F]) extends proc.Process1x2[A, E, F, R](processType, ichan1, ochan1, ochan2) with Process[R] {
  def behavior =
    IO.open(1, ichan1) ~ IO.open(1, ochan1) ~ IO.open(2, ochan2) >>\ processType.main

}

private[io] class Process2x2[A: Message, B: Message, E: Message, F: Message, R](processType: io.ProcessType2x2[A, B, E, F, R], ichan1: IChan[A], ichan2: IChan[B], ochan1: OChan[E], ochan2: OChan[F]) extends proc.Process2x2[A, B, E, F, R](processType, ichan1, ichan2, ochan1, ochan2) with Process[R] {
  def behavior =
    IO.open(1, ichan1) ~ IO.open(2, ichan2) ~ IO.open(1, ochan1) ~ IO.open(1, ochan2) >>\ processType.main

}

private[io] class Process3x0[A: Message, B: Message, C: Message, R](processType: io.ProcessType3x0[A, B, C, R], ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C]) extends proc.Process3x0[A, B, C, R](processType, ichan1, ichan2, ichan3) with Process[R] {
  def behavior =
    IO.open(1, ichan1) ~ IO.open(2, ichan2) ~ IO.open(3, ichan3) >>\ processType.main

}

private[io] class Process3x1[A: Message, B: Message, C: Message, E: Message, R](processType: io.ProcessType3x1[A, B, C, E, R], ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C], ochan1: OChan[E]) extends proc.Process3x1[A, B, C, E, R](processType, ichan1, ichan2, ichan3, ochan1) with Process[R] {
  def behavior =
    IO.open(1, ichan1) ~ IO.open(2, ichan2) ~ IO.open(3, ichan3) ~ IO.open(1, ochan1) >>\ processType.main

}
