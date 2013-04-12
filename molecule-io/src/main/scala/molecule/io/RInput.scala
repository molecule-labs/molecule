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
package io

import stream.{ IChan, OChan }
import channel.RIChan

/**
 * A process-level input channel that can be released.
 *
 *  @tparam  A    the type of the input's messages
 *
 */
trait RInput[+A] {

  /**
   * Forward asynchronously the content of this input to an output. The
   * output will be closed with the same signal as the input. The output and
   * input are released from this process and the method returns immediately.
   *
   *
   *  @param  output the output to connect to this input
   *  @return the signal indicating which channel was closed first
   */
  def connect(output: Output[A]): IO[RIChan[Either[Signal, Signal]]]

  /**
   * Forward asynchronously the content of this input to an first-class output
   * channel. The output will be closed with the same signal as the input. The output and
   * input are released from this process and the method returns immediately.
   *
   *
   *  @param  output the output to connect to this input
   *  @return the signal indicating which channel was closed first
   */
  def connect(ochan: OChan[A]): IO[RIChan[Either[Signal, Signal]]]

  /**
   * Release the first-class input channel from this process.
   *
   *  @param  ochan the output to connect to this input
   */
  def release(): IO[IChan[A]]

  /**
   * Interleave the messages produced asynchronously on this channel with the messages
   * produced on an other channel.
   * This builds a new input channel that produces one message of type Either[A, B] for every
   * message produce on this or the other channel, where `A` is the type of messages produced
   * on this channel and `B` is the type of messages
   * produced on the other channel. By default, the resulting channel is closed when both
   * input channels are closed.
   *
   * @param right the other input channel
   * @return a new input channel that produces one message of type Either[A, B] for every
   * message produce on this or the other channel
   */
  def interleave[B: Message](right: RInput[B]): IO[Input[Either[A, B]]]

  /**
   * Merge the streams of two channels.
   * This builds a new input channel that produces messages from `this` channel and the other channel.
   *
   * @param right the other input channel
   * @return a new input channel that produces messages coming on both input channels
   */
  def merge[B >: A: Message](right: RInput[B]): IO[Input[B]]

}