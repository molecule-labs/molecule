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

import stream.IChan

/**
 * Process-level streaming input channel interface.
 *
 *  @tparam  A    the type of the input's messages
 *
 */
trait SInput[+A] extends WInput[A] {

  /**
   * Builds a new input by applying a function to all messages of this input.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned input.
   *  @return       a new input resulting from applying the given function
   *                `f` to each message of this input.
   *
   *  @usecase def map[B](f: A => B): SInput[B]
   */
  def map[B: Message](f: A => B): SInput[B]

  /**
   * Builds a new debugging input that prints every message received.
   *
   *  @param label  the label to put in front of each debug line.
   *  @param f      A function converting messages to a string.
   *  @return       The same input excepted each message will be printed.
   *
   */
  def debug(label: String, f: A => String = _.toString): SInput[A]

  /**
   * Builds a new input by applying a partial function to all messages of this input
   *  on which the function is defined.
   *
   *  @param pf     the partial function which filters and maps the input.
   *  @tparam B     the message type of the returned collection.
   *  @return       a new input resulting from applying the partial function
   *                `pf` to each message on which it is defined.
   *                The order of the messages is preserved.
   *
   *  @usecase def collect[B](pf: PartialFunction[A, B]): SInput[B]
   */
  def collect[B: Message](pf: PartialFunction[A, B]): SInput[B]

  /**
   * Builds a new input by applying a function to all messages of this input
   *  and concatenating the results.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned input.
   *  @return       a new input resulting from applying the given collection-valued function
   *                `f` to each message of this input and concatenating the results.
   *
   *  @usecase def flatMap[B](f: A => Seg[B]): SInput[B]
   *
   *  @return       a new input resulting from applying the given collection-valued function
   *                `f` to each message of this input and concatenating the results.
   */
  def flatMap[B: Message](f: A => Seg[B]): SInput[B]

  /**
   * Produces an input containing cummulative results of applying the operator going first to last message.
   *
   * @tparam B      the type of the messages in the resulting input
   * @param z       the initial state
   * @param op      the binary operator applied to the intermediate state and the message
   * @return        input with intermediate results
   */
  def smap[S, B: Message](z: S)(fsm: (S, A) => (S, B)): SInput[B]

  /**
   * Produces an input resulting from applying a parser combinator to this input stream.
   *
   * @tparam B      the type of the messages in the resulting input
   * @tparam C      the type of the messages parsed
   * @param parser  a parser combinator
   * @return        input with parsed results
   */
  def parse[C >: A: Message, B: Message](parser: parsing.Parser[C, B]): SInput[B]

  /**
   * Produces a collection containing cummulative results of applying the operator going first to last message.
   *
   * @tparam B      the type of the messages in the resulting input
   * @param z       the initial value
   * @param op      the binary operator applied to the intermediate result and the message
   * @return        input with intermediate results
   */
  def scan[B: Message](z: B)(op: (B, A) => B): SInput[B]

  /**
   * Selects all messages of this input which satisfy a predicate.
   *
   *  @param p     the predicate used to test messages.
   *  @return      a new input consisting of all messages of this input that satisfy the given
   *               predicate `p`. The order of the messages is preserved.
   */
  def filter(p: A => Boolean): SInput[A]

  /**
   * Converts this input stream of traversable collections into
   *  an input stream in which all message collections are concatenated.
   *  @tparam B the type of the messages of each traversable collection.
   *  @param asTraversable an implicit conversion which asserts that the message type of this
   *         input is a `Traversable`.
   *  @param message an implicit message definition for the message type of the
   *         `Traversable`.
   *  @return a new input resulting from concatenating all the `Traversable` collections.
   *  @usecase def flatten[B]: SInput[B]
   */
  def flatten[B](implicit message: Message[B], asTraversable: A => /*<:<!!!*/ Traversable[B]): SInput[B]

  /**
   * Prepend a message on this input.
   *
   *  @param x the message to prepend.
   *  @return  an input which produces `x` as first message and
   *           which continues with the remaining of the stream.
   *  @usecase def ::(x: A): SInput[A]
   */
  final def ::[B >: A: Message](x: B): Input[B] =
    this.+:[B](x)

  /**
   * Prepend a message on this input.
   *
   *  @param x the message to prepend.
   *  @return  an input which produces `x` as first message and
   *           which continues with the remaining of the stream.
   *  @usecase def +:(x: A): SInput[A]
   */
  def +:[B >: A: Message](x: B): Input[B]

  /**
   * Prepend the messages of a given segment in front of this input.
   *  @param prefix  The segment to prepend.
   *  @return  an input which produces that segment first and
   *           then continues with the remaining of the stream.
   *  @usecase def ++:(prefix: Seg[A]): Input[A]
   */
  def ++:[B >: A: Message](prefix: Seg[B]): SInput[B]

  /**
   * Skip the first ''n'' messages of this input, or the complete message stream,
   *  if it contains less messages.
   *
   *  @param   n the number of elements to drop
   *  @return  an input which produces all messages of the current input, except
   *           it omits the first `n` values.
   */
  // TODO

  /**
   * Skips longest sequence of elements of this input which satisfy given
   *  predicate `p`, and returns an input of the remaining elements.
   *
   *  @param p the predicate used to skip elements.
   *  @return  an input producing the remaining elements
   */
  def dropWhile(p: A => Boolean): SInput[A]

  /**
   * Returns an input which groups messages produced by this input
   * into fixed size blocks. The last group may contain less messages
   * the number of messages receives is not a multiple of the group
   * size.
   *
   *  @param size the size of the groups.
   *  @return  an input producing the groups
   */
  def grouped(size: Int): SInput[Seg[A]]

  /**
   * Create an input that produces first ''n'' messages of this input. The
   * current input cannot be manipulated until the last value of the new input
   * has been consumed.
   *
   *  @param  n    the number of messages to take
   *  @return an input producing only of the first `n` messages of this input,
   *          or less than `n` messages if the input produces less messages.
   */
  def take(size: Int): SInput[A]

  /**
   * Fold left
   */
  def fold[B: Message](z: B)(f: (B, A) => B): IO[B]

  /**
   * Create an input that produces longest sequence of this input that satisfy
   * predicate `p`. The  current input cannot be manipulated until the last value
   * of the new input has been consumed.
   *
   *  @param  p    the predicate to satisfy
   *  @return an input producing only of the first messages that satisfy
   *          the predicate.
   */
  def span(p: A => Boolean): SInput[A]

  /**
   * Builds a new input that compresses the content of each segment into a single value.
   *
   *  @param f      A function converting segments to a single value.
   *  @return       The compressed input.
   */
  def compress[B: Message](f: seg.Seg[A] => B): SInput[B]

  /**
   * Forward the content of this input to an output. It returns when this input is
   * empty but does not close the output.
   *
   *  @param  output the output on which to forward this content
   *  @return the signal that closes this input
   */
  def forward(output: Output[A]): IO[Signal]

  // Used for forwarding from an input to an output
  protected[io] def extractSeg(t: impl.UThreadContext, k: seg.Seg[A] => Unit, klast: (seg.Seg[A], Signal) => Unit): Unit

  /**
   * Close/poison this input
   *
   *  @param  signal the poisoning signal
   */
  @deprecated("Use `poison` instead", "3.0")
  def close(signal: Signal): IO[Unit] = poison(signal)

  /**
   * Poison this input
   *
   *  @param  signal the poisoning signal
   */
  def poison(signal: Signal): IO[Unit]

  /**
   * Close/poison this input with EOS signal
   */
  @deprecated("Use `poison` instead", "3.0")
  def close(): IO[Unit] = poison()

  /**
   * Poison this input with EOS signal
   */
  def poison(): IO[Unit]
}
