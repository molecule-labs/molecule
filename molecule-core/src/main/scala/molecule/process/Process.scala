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

import platform.UThread
import channel.ROChan
import stream.{ IChan, OChan }

/**
 * Instance of a ProcessType that has been bound to input and
 * output channels but not yet running.
 *
 */
trait Process[+R] { outer =>

  /**
   * Type of this process.
   *
   * @return the process type
   */
  def ptype: ProcessType

  /**
   * Start this process by assigning it to a new user-level thread and a return channel.
   *
   * @param t the user-level thread
   * @param rchan the return channel
   * @return unit
   */
  def start(t: UThread, rchan: ROChan[R]): Unit

  /**
   * Apply a function `f` to the result of this process.
   *
   * @param f the function to apply to result.
   * @return a new process with the result
   */
  def map[B](f: R => B): Process[B] = new Process[B] {
    def ptype = outer.ptype
    def start(t: UThread, rchan: ROChan[B]): Unit =
      outer.start(t, rchan.map(f))
  }

  /**
   * Apply a function `f` that starts a new process on the result of this process.
   *
   * @param f the function to apply to result.
   * @return the new process created by `f`
   */
  def flatMap[B](f: R => Process[B]): Process[B] = new Process[B] {
    def ptype = outer.ptype
    def start(t: UThread, rchan: ROChan[B]): Unit =
      outer.start(t, new ROChan[R] {
        def done(v: Either[Signal, R]) = v match {
          case Right(r) => f(r).start(t, rchan)
          case Left(signal) => rchan.done(Left(signal))
        }
      })
  }

  override final def toString =
    ptype.name + "/" + System.identityHashCode(this)
}

abstract class Process0x0[R](
  val ptype: ProcessType0x0[R]) extends Process[R]

/**
 * A Process bound to channels that may be serialized and sent
 * remotely (TBD)
 */
abstract class Process1x0[A, R](
  val ptype: ProcessType1x0[A, R],
  val ichan1: IChan[A]) extends Process[R]

abstract class Process0x1[A, R](
  val ptype: ProcessType0x1[A, R],
  val ochan1: OChan[A]) extends Process[R]

abstract class Process1x1[A, B, R](
  val ptype: ProcessType1x1[A, B, R],
  val ichan1: IChan[A],
  val ochan1: OChan[B]) extends Process[R]

abstract class Process2x0[A, B, R](
  val ptype: ProcessType2x0[A, B, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B]) extends Process[R]

abstract class Process2x1[A, B, C, R](
  val ptype: ProcessType2x1[A, B, C, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ochan1: OChan[C]) extends Process[R]

abstract class Process0x2[C, D, R](
  val ptype: ProcessType0x2[C, D, R],
  val ochan1: OChan[C],
  val ochan2: OChan[D]) extends Process[R]

abstract class Process1x2[A, C, D, R](
  val ptype: ProcessType1x2[A, C, D, R],
  val ichan1: IChan[A],
  val ochan1: OChan[C],
  val ochan2: OChan[D]) extends Process[R]

abstract class Process2x2[A, B, C, D, R](
  val ptype: ProcessType2x2[A, B, C, D, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ochan1: OChan[C],
  val ochan2: OChan[D]) extends Process[R]

abstract class Process3x0[A, B, C, R](
  val ptype: ProcessType3x0[A, B, C, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ichan3: IChan[C]) extends Process[R]

abstract class Process3x1[A, B, C, D, R](
  val ptype: ProcessType3x1[A, B, C, D, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ichan3: IChan[C],
  val ochan1: OChan[D]) extends Process[R]

abstract class Process3x2[A, B, C, D, E, R](
  val ptype: ProcessType3x2[A, B, C, D, E, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ichan3: IChan[C],
  val ochan1: OChan[D],
  val ochan2: OChan[E]) extends Process[R]

abstract class Process0x3[D, E, F, R](
  val ptype: ProcessType0x3[D, E, F, R],
  val ochan1: OChan[D],
  val ochan2: OChan[E],
  val ochan3: OChan[F]) extends Process[R]

abstract class Process1x3[A, D, E, F, R](
  val ptype: ProcessType1x3[A, D, E, F, R],
  val ichan1: IChan[A],
  val ochan1: OChan[D],
  val ochan2: OChan[E],
  val ochan3: OChan[F]) extends Process[R]

abstract class Process2x3[A, B, D, E, F, R](
  val ptype: ProcessType2x3[A, B, D, E, F, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ochan1: OChan[D],
  val ochan2: OChan[E],
  val ochan3: OChan[F]) extends Process[R]

abstract class Process3x3[A, B, C, D, E, F, R](
  val ptype: ProcessType3x3[A, B, C, D, E, F, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ichan3: IChan[C],
  val ochan1: OChan[D],
  val ochan2: OChan[E],
  val ochan3: OChan[F]) extends Process[R]

abstract class Process4x0[A, B, C, D, R](
  val ptype: ProcessType4x0[A, B, C, D, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ichan3: IChan[C],
  val ichan4: IChan[D]) extends Process[R]

abstract class Process4x1[A, B, C, D, E, R](
  val ptype: ProcessType4x1[A, B, C, D, E, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ichan3: IChan[C],
  val ichan4: IChan[D],
  val ochan1: OChan[E]) extends Process[R]

abstract class Process4x2[A, B, C, D, E, F, R](
  val ptype: ProcessType4x2[A, B, C, D, E, F, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ichan3: IChan[C],
  val ichan4: IChan[D],
  val ochan1: OChan[E],
  val ochan2: OChan[F]) extends Process[R]

abstract class Process4x3[A, B, C, D, E, F, G, R](
  val ptype: ProcessType4x3[A, B, C, D, E, F, G, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ichan3: IChan[C],
  val ichan4: IChan[D],
  val ochan1: OChan[E],
  val ochan2: OChan[F],
  val ochan3: OChan[G]) extends Process[R]

abstract class Process0x4[E, F, G, H, R](
  val ptype: ProcessType0x4[E, F, G, H, R],
  val ochan1: OChan[E],
  val ochan2: OChan[F],
  val ochan3: OChan[G],
  val ochan4: OChan[H]) extends Process[R]

abstract class Process1x4[A, E, F, G, H, R](
  val ptype: ProcessType1x4[A, E, F, G, H, R],
  val ichan1: IChan[A],
  val ochan1: OChan[E],
  val ochan2: OChan[F],
  val ochan3: OChan[G],
  val ochan4: OChan[H]) extends Process[R]

abstract class Process2x4[A, B, E, F, G, H, R](
  val ptype: ProcessType2x4[A, B, E, F, G, H, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ochan1: OChan[E],
  val ochan2: OChan[F],
  val ochan3: OChan[G],
  val ochan4: OChan[H]) extends Process[R]

abstract class Process3x4[A, B, C, E, F, G, H, R](
  val ptype: ProcessType3x4[A, B, C, E, F, G, H, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ichan3: IChan[C],
  val ochan1: OChan[E],
  val ochan2: OChan[F],
  val ochan3: OChan[G],
  val ochan4: OChan[H]) extends Process[R]

abstract class Process4x4[A, B, C, D, E, F, G, H, R](
  val ptype: ProcessType4x4[A, B, C, D, E, F, G, H, R],
  val ichan1: IChan[A],
  val ichan2: IChan[B],
  val ichan3: IChan[C],
  val ichan4: IChan[D],
  val ochan1: OChan[E],
  val ochan2: OChan[F],
  val ochan3: OChan[G],
  val ochan4: OChan[H]) extends Process[R]
