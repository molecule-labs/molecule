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

import platform.UThread

/**
 * A stream output channel.
 *
 *  @tparam  A    the type of the output's messages
 */
abstract class OChan[-A] {

  /**
   * Write a segment on this channel.
   *
   * @param t the user-level thread
   * @param data the segment and an optional termination signal
   * @param k the continuation invoked once the message has been written
   */
  def write(t: UThread, seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit)

  /**
   * Add a stream transformer to this channel.
   *
   * @param t the transformer
   *
   * @return the transformed channel
   */
  def add[B: Message](t: OChan[A] => OChan[B]): OChan[B]

  /**
   * Close the channel
   *
   * @param the signal
   */
  def close(signal: Signal): Unit

  /**
   * Write the last segment
   *
   * Note that this must flush any buffered channel (@see BufferedOChan).
   *
   * @param seg the last segment
   * @param the signal
   */
  def close(t: UThread, seg: Seg[A], signal: Signal): Unit =
    write(t, seg, Some(signal), utils.NOOP)

  /**
   * Write last value
   *
   * @param a the last value
   * @param the signal
   */
  def close(t: UThread, a: A, signal: Signal): Unit =
    close(t, Seg(a), signal)

  /**
   * Builds a new output by applying a function to all the messages
   * wrote to it.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned input.
   *  @return       a new input resulting from applying the given function
   *                `f` to each message of this input.
   *
   *  @usecase def map[B](f: B => A): OChan[B]
   */
  def map[B: Message](f: B => A): OChan[B] =
    add(ochan.immutable.MapperT(1, f))

  /**
   * Builds a new output that applies a partial function to all messages on which
   * the function is defined and filter out the others.
   *
   *  @param pf     the partial function which filters and maps the stream elements.
   *  @tparam B     the message type of the returned output channel.
   *  @return       a new output resulting from applying the partial function
   *                `pf` to each message on which it is defined.
   *                The order of the messages is preserved.
   *
   *  @usecase def collect[B](pf: PartialFunction[B, A]): OChan[B]
   */
  def collect[B: Message](f: PartialFunction[B, A]): OChan[B] =
    add(ochan.immutable.PartialMapperT(1, f))

  /**
   * Produces an output resulting from applying a repeatedly a parser combinator
   * to its messages.
   *
   * @tparam B      the type of the messages of the resulting output
   * @param parser  a parser combinator
   * @return        output which parses its messages
   */
  def parse[B: Message](parse: parsing.Parser[B, A]): OChan[B] =
    add(ochan.immutable.ParserT(1, parse, parse))

  /**
   * Builds a new debugging output that prints every message received.
   *
   *  @param label  the label to put in front of each debug line.
   *  @param f      A function converting messages to a string (defaults to _.toString).
   *  @return       The same output excepted each message will be printed.
   */
  def debug[B <: A: Message](label: String, f: B => String = { b: B => b.toString }): OChan[B] = {
    val id = System.identityHashCode(this)
    val h = "DBG:O[" + id + "]:" + label + ":"
    add(new ochan.immutable.DebugT({
      case Right(a) => println(h + f(a))
      case Left(s) => println(h + s)
    }))
  }

  /**
   * Builds an output producing cummulative results of applying the operator going
   * from first to last message written.
   *
   * @tparam B      the type of the messages accepted by the resulting output
   * @param z       the initial state
   * @param op      the binary operator applied to the intermediate state and the message
   * @return        output producing intermediate results
   */
  def smap[S, B: Message](z: S)(fsm: (S, B) => (S, A)): OChan[B] =
    add(ochan.immutable.StateT(1, z, fsm))

  /**
   * Builds a new output that applies a function to all messages written
   *  to it and concatenates the results.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned output.
   *  @return       a new output resulting from applying the given collection-valued function
   *                `f` to each message written and concatenating the results.
   *
   *  @usecase def flatMap[B](f: B => Traversable[A]): OChan[B]
   *
   *  @return       a new input resulting from applying the given collection-valued function
   *                `f` to each message of this input and concatenating the results.
   */
  def flatMap[B: Message](f: B => Seg[A]): OChan[B] =
    add(ochan.immutable.FlatMapperT(1, f))

  /**
   * Builds a new output that applies a function to every messages written
   *  to it and concatenates the resulting segments.
   *
   *  @param f      the function to apply to each message and returning a segment.
   *  @tparam B     the message type of the returned output.
   *  @return       a new output resulting from applying the given collection-valued function
   *                `f` to each message written and concatenating the results.
   *
   *  @usecase def encode[B](f: B => Seg[A]): OChan[B]
   */
  def encode[B: Message](f: B => Seg[A]): OChan[B] =
    add(ochan.immutable.FlatSegMapperT(1, f))

  /**
   * Builds a new output that applies a function to every segment written
   *  to it.
   *
   *  @param f      the function to apply to each segment and returning a single message.
   *  @tparam B     the message type of the returned output.
   *  @return       a new output resulting from applying the given collection-valued function
   *                `f` to each segment written to it.
   *
   *  @usecase def compress[B](f: Seg[B] => A): OChan[B]
   */
  def compress[B: Message](f: Seg[B] => A): OChan[B] =
    add(ochan.immutable.CompressorT(1, f))

  /**
   * Adds a message in front of this output.
   *  @param x the message to prepend.
   *  @return  an output which produces `x` as first message and
   *           which continues with the remaining of the stream.
   *  @usecase def :+:(x: A): OChan[A]
   */
  def :+:[B <: A: Message](x: B): OChan[B] =
    this.:+:(Seg(x))

  /**
   * Adds a segment in front of this output.
   *  @param seg the segment to prepend.
   *  @return  an output which produces `seg` as first segment and
   *           which continues with the remaining of the stream.
   *  @usecase def :+:(seg: Seg[A]): OChan[A]
   */
  def :+:[B <: A: Message](seg: Seg[B]): OChan[B] =
    add(ochan.immutable.BufferT(seg))

  import molecule.process.Process

  /**
   * Flush the content of an input to this output.
   * It stops as soon as one of the two channel is poisoned and it does
   * not propagate the poison signal.
   *
   *  @param  input the input to flush.
   *  @return A stream that returns the channels after the flush operation.
   *          If the stream completed successfully, then the input channel is closed,
   *          otherwise the output channel is poisoned.
   */
  def flush[B <: A](ichan: IChan[B]): Process[(IChan[B], OChan[B])] =
    process.core.Forward(ichan, this)

}

object OChan {

  /**
   * Create an output channel closed with the given signal.
   *
   *  @param signal the signal
   *  @return an output channel closed with the given signal.
   */
  def apply[A: Message](signal: Signal): OChan[A] =
    ochan.NilOChan[A](signal)

  def unapply(chan: OChan[_]): Option[Signal] =
    if (chan.isInstanceOf[ochan.NilOChan[_]]) Some(chan.asInstanceOf[ochan.NilOChan[_]].signal) else None

  private[this] val OChanIsPoisonlable = new Message[OChan[Nothing]] {
    def poison(message: OChan[Nothing], signal: Signal): Unit = {
      message.close(signal)
    }
  }

  implicit def ochanIsMessage[A]: Message[OChan[A]] = OChanIsPoisonlable

  /**
   * Create a stream output channel from a system output channel.
   *
   * @param ch the system-level channel
   *
   * @return the stream output channel
   */
  def lift[A: Message](ch: channel.OChan[A]): OChan[A] =
    ochan.BackOChan(ch)

  /**
   * Lift Stream output channel factory to the system-level input channel factory.
   * This lift adds additional factory methods, which do return system-level
   * input channels, but the latter will be implicitly lifted to stream input channels.
   */
  @inline
  final implicit def liftOChanCompanion(o: OChan.type): channel.OChan.type = channel.OChan

}
