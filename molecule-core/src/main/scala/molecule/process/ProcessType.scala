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

import channel.ROChan
import stream.{ IChan, OChan }

/**
 * Base interface for process types.
 */
trait ProcessType {
  /**
   * The name of this process type
   *
   * @return The name of this process type
   */
  def name: String = this.getClass.getName
}

/*
 ** Marker traits
 */

/**
 * Process types
 */
abstract class ProcessType0x0[R] extends ProcessType with (() => Process0x0[R]) {

  def apply(): Process0x0[R]

}

import platform.UThread

abstract class ProcessType1x0[A, R] extends ProcessType with (IChan[A] => Process1x0[A, R]) { outer =>

  def apply(ichan: IChan[A]): Process1x0[A, R]

  final def adapt[B](f: IChan[B] => IChan[A]): ProcessType1x0[B, R] = new ProcessType1x0[B, R] {
    override def name = outer.name
    def apply(ichan: IChan[B]): Process1x0[B, R] = {
      val p = outer.apply(f(ichan))
      new Process1x0[B, R](this, ichan) {
        def start(t: UThread, rchan: ROChan[R]) = p.start(t, rchan)
      }
    }
  }
}

abstract class ProcessType0x1[A, R] extends ProcessType with (OChan[A] => Process0x1[A, R]) { outer =>

  def apply(ochan: OChan[A]): Process0x1[A, R]

  def adapt[B](f: OChan[B] => OChan[A]): ProcessType0x1[B, R] = new ProcessType0x1[B, R] {
    override def name = outer.name
    def apply(ochan: OChan[B]): Process0x1[B, R] = {
      val p = outer.apply(f(ochan))
      new Process0x1[B, R](this, ochan) {
        def start(t: UThread, rchan: ROChan[R]) = p.start(t, rchan)
      }
    }
  }

}

abstract class ProcessType1x1[A, B, R] extends ProcessType
    with ((IChan[A], OChan[B]) => Process1x1[A, B, R]) { outer =>

  def apply(ichan: IChan[A], ochan: OChan[B]): Process1x1[A, B, R]

  def adapt[C, D](f1: IChan[C] => IChan[A], f2: OChan[D] => OChan[B]): ProcessType1x1[C, D, R] = new ProcessType1x1[C, D, R] {
    override def name = outer.name
    def apply(ichan: IChan[C], ochan: OChan[D]): Process1x1[C, D, R] = {
      val p = outer.apply(f1(ichan), f2(ochan))
      new Process1x1[C, D, R](this, ichan, ochan) {
        def start(t: UThread, rchan: ROChan[R]) = p.start(t, rchan)
      }
    }
  }
}

abstract class ProcessType2x0[A, B, R] extends ProcessType
    with ((IChan[A], IChan[B]) => Process2x0[A, B, R]) {

  def apply(ichan1: IChan[A], ichan2: IChan[B]): Process2x0[A, B, R]
}

abstract class ProcessType2x1[A, B, E, R] extends ProcessType with ((IChan[A], IChan[B], OChan[E]) => Process2x1[A, B, E, R]) { outer =>

  def apply(ichan1: IChan[A], ichan2: IChan[B], ochan: OChan[E]): Process2x1[A, B, E, R]

  def apply(ichan1: => IChan[A]): ProcessType1x1[B, E, R] = new ProcessType1x1[B, E, R] {
    override def name = outer.name
    def apply(ichan: IChan[B], ochan: OChan[E]): Process1x1[B, E, R] = {
      val p = outer.apply(ichan1, ichan, ochan)
      new Process1x1[B, E, R](this, ichan, ochan) {
        def start(t: UThread, rchan: ROChan[R]) = p.start(t, rchan)
      }
    }
  }

}

abstract class ProcessType0x2[E, F, R] extends ProcessType
    with ((OChan[E], OChan[F]) => Process0x2[E, F, R]) {

  def apply(ochan1: OChan[E], ochan2: OChan[F]): Process0x2[E, F, R]
}

abstract class ProcessType1x2[A, E, F, R] extends ProcessType
    with ((IChan[A], OChan[E], OChan[F]) => Process1x2[A, E, F, R]) {

  def apply(ichan: IChan[A], ochan1: OChan[E], ochan2: OChan[F]): Process1x2[A, E, F, R]
}

abstract class ProcessType2x2[A, B, E, F, R] extends ProcessType
    with ((IChan[A], IChan[B], OChan[E], OChan[F]) => Process2x2[A, B, E, F, R]) {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ochan1: OChan[E], ochan2: OChan[F]): Process2x2[A, B, E, F, R]
}

abstract class ProcessType3x0[A, B, C, R] extends ProcessType
    with ((IChan[A], IChan[B], IChan[C]) => Process3x0[A, B, C, R]) {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C]): Process3x0[A, B, C, R]
}

abstract class ProcessType3x1[A, B, C, E, R] extends ProcessType
    with ((IChan[A], IChan[B], IChan[C], OChan[E]) => Process3x1[A, B, C, E, R]) {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C],
    ochan1: OChan[E]): Process3x1[A, B, C, E, R]
}

abstract class ProcessType3x2[A, B, C, E, F, R] extends ProcessType
    with ((IChan[A], IChan[B], IChan[C], OChan[E], OChan[F]) => Process3x2[A, B, C, E, F, R]) {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C],
    ochan1: OChan[E], ochan2: OChan[F]): Process3x2[A, B, C, E, F, R]
}

abstract class ProcessType0x3[E, F, G, R] extends ProcessType
    with ((OChan[E], OChan[F], OChan[G]) => Process0x3[E, F, G, R]) {

  def apply(ochan1: OChan[E], ochan2: OChan[F], ochan3: OChan[G]): Process0x3[E, F, G, R]
}

abstract class ProcessType1x3[A, E, F, G, R] extends ProcessType
    with ((IChan[A], OChan[E], OChan[F], OChan[G]) => Process1x3[A, E, F, G, R]) {

  def apply(ichan1: IChan[A],
    ochan1: OChan[E], ochan2: OChan[F], ochan3: OChan[G]): Process1x3[A, E, F, G, R]
}

abstract class ProcessType2x3[A, B, E, F, G, R] extends ProcessType
    with ((IChan[A], IChan[B], OChan[E], OChan[F], OChan[G]) => Process2x3[A, B, E, F, G, R]) {

  def apply(ichan1: IChan[A], ichan2: IChan[B],
    ochan1: OChan[E], ochan2: OChan[F], ochan3: OChan[G]): Process2x3[A, B, E, F, G, R]
}

abstract class ProcessType3x3[A, B, C, E, F, G, R] extends ProcessType
    with ((IChan[A], IChan[B], IChan[C], OChan[E], OChan[F], OChan[G]) => Process3x3[A, B, C, E, F, G, R]) {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C],
    ochan1: OChan[E], ochan2: OChan[F], ochan3: OChan[G]): Process3x3[A, B, C, E, F, G, R]
}

abstract class ProcessType4x0[A, B, C, D, R] extends ProcessType {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C], ichan4: IChan[D]): Process4x0[A, B, C, D, R]
}

abstract class ProcessType4x1[A, B, C, D, E, R] extends ProcessType {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C], ichan4: IChan[D],
    ochan1: OChan[E]): Process4x1[A, B, C, D, E, R]
}

abstract class ProcessType4x2[A, B, C, D, E, F, R] extends ProcessType {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C], ichan4: IChan[D],
    ochan1: OChan[E], ochan2: OChan[F]): Process4x2[A, B, C, D, E, F, R]
}

abstract class ProcessType4x3[A, B, C, D, E, F, G, R] extends ProcessType {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C], ichan4: IChan[D],
    ochan1: OChan[E], ochan2: OChan[F], ochan3: OChan[G]): Process4x3[A, B, C, D, E, F, G, R]
}

abstract class ProcessType0x4[E, F, G, H, R] extends ProcessType {

  def apply(ochan1: OChan[E], ochan2: OChan[F], ochan3: OChan[G], ochan4: OChan[H]): Process0x4[E, F, G, H, R]
}

abstract class ProcessType1x4[A, E, F, G, H, R] extends ProcessType {

  def apply(ichan1: IChan[A],
    ochan1: OChan[E], ochan2: OChan[F], ochan3: OChan[G], ochan4: OChan[H]): Process1x4[A, E, F, G, H, R]
}

abstract class ProcessType2x4[A, B, E, F, G, H, R] extends ProcessType {

  def apply(ichan1: IChan[A], ichan2: IChan[B],
    ochan1: OChan[E], ochan2: OChan[F], ochan3: OChan[G], ochan4: OChan[H]): Process2x4[A, B, E, F, G, H, R]
}

abstract class ProcessType3x4[A, B, C, E, F, G, H, R] extends ProcessType {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C],
    ochan1: OChan[E], ochan2: OChan[F], ochan3: OChan[G], ochan4: OChan[H]): Process3x4[A, B, C, E, F, G, H, R]
}

abstract class ProcessType4x4[A, B, C, D, E, F, G, H, R] extends ProcessType {

  def apply(ichan1: IChan[A], ichan2: IChan[B], ichan3: IChan[C], ichan4: IChan[D],
    ochan1: OChan[E], ochan2: OChan[F], ochan3: OChan[G], ochan4: OChan[H]): Process4x4[A, B, C, D, E, F, G, H, R]
}
