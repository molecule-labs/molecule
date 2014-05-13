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
 * A stream input channel.
 *
 *  @tparam  A    the type of the input's messages
 *
 */
abstract class IChan[+A] { outer =>

  /**
   * Tracks the complexity, that is the number of transformations
   * stacked up on this channel.
   *
   * @return the complexity
   */
  private[molecule] def complexity: Int

  /**
   * Read a segment on this channel.
   *
   * @param t the user-level thread
   * @param k the continuation invoked if there is a message
   */
  def read(t: UThread, k: (Seg[A], IChan[A]) => Unit): Unit

  /**
   * Add a transformer to this channel.
   *
   * @param t the transformer
   *
   * @return the transformed channel
   */
  def add[B: Message](t: IChan[A] => IChan[B]): IChan[B]

  /**
   * Poison this channel. Any pending read continuation will be
   * invoked with Seg.Nil and NilIChan(signal) as argument.
   *
   * @param signal the signal
   */
  def poison(signal: Signal)

  /**
   * Read one message on this channel.
   *
   * @param t the user-level thread
   * @param more the continuation invoked if there is a message
   * @param empty the continuation invoked if the channel is closed
   */
  def step(t: UThread)(more: (A, IChan[A]) => Unit)(empty: Signal => Unit)(implicit m: Message[A]): Unit =
    if (outer.isInstanceOf[ichan.NilIChan]) {
      empty(outer.asInstanceOf[ichan.NilIChan].signal)
    } else
      read(t, {
        case (Seg.Nil, next) =>
          next.step(t)(more)(empty)
        case (seg, next) =>
          more(seg.head, seg.tail ++: next)
      })

  /**
   * Prepend a message on this channel.
   *
   *  @param x the message to prepend.
   *  @return  an input which produces `x` as first message and
   *           which continues with the remaining of the stream.
   *  @usecase def ::(x: A): IChan[A]
   */
  def ::[B >: A: Message](b: B): IChan[B] =
    this.++:(seg.Seg(b))

  /**
   * Prepend a message on this channel.
   *
   *  @param x the message to prepend.
   *  @return  an input which produces `x` as first message and
   *           which continues with the remaining of the stream.
   *  @usecase def +:(x: A): IChan[A]
   */
  def +:[B >: A: Message](b: B): IChan[B] =
    this.++:(seg.Seg(b))

  /**
   * Prepend a segment.
   *
   *  @param seg  the segment to prepend.
   *  @return  an input which produces the segment and
   *           which continues with the remaining of the stream.
   *  @usecase def ++:[B](s: Seg[B]): IChan[B]
   */
  def ++:[B >: A: Message](seg: Seg[B]): IChan[B] =
    add(ichan.immutable.BufferT(seg))

  /**
   * Builds a new input by applying a function to all messages of this input.
   *
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned input.
   *  @return       a new input resulting from applying the given function
   *                `f` to each message of this input.
   *
   *  @usecase def map[B](f: A => B): IChan[B]
   */
  def map[B: Message](f: A => B): IChan[B] =
    add(ichan.immutable.MapperT(1, f))

  /**
   * Builds a new input by applying a function to all segments of this input.
   *
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned input.
   *  @return       a new input resulting from applying the given function
   *                `f` to each segment of this input.
   *
   *  @usecase def mapSeg[B](f: Seg[A] => Seg[B]): IChan[B]
   */
  def mapSeg[B: Message](f: Seg[A] => Seg[B]): IChan[B] =
    add(ichan.immutable.SegMapperT(1, f))

  /**
   * Builds a new debugging input that prints every message received.
   *
   *  @param label  the label to put in front of each debug line.
   *  @param f      A function converting messages to a string (defaults to _.toString).
   *  @return       The same input excepted each message will be printed.
   */
  def debug(label: String, f: A => String = _.toString)(implicit ma: Message[A]): IChan[A] = {
    val id = System.identityHashCode(this)
    val h = "DBG:I[" + id + "]:" + label + ":"
    add(new ichan.immutable.DebugT({
      case Right(a) => println(h + f(a))
      case Left(s) => println(h + s)
    }))
  }

  /**
   * Builds a new input that compresses the content of each segment into a single value.
   *
   *  @param f      A function converting segments to a single value.
   *  @return       The compressed input.
   */
  def compress[B: Message](f: Seg[A] => B): IChan[B] =
    add(ichan.immutable.CompressorT(1, f))

  /**
   * Builds a new input that produces its underlying segments.
   *
   *  @return       The deflated input.
   */
  //  def deflate[B >: A :Message]:IChan[Seg[B]] =
  //    add(ichan.immutable.CompressorT(1, identity[Seg[A]]))

  /**
   * Produces an input containing cummulative results of applying the operator going first to last message.
   *
   * @tparam B      the type of the messages in the resulting input
   * @param z       the initial state
   * @param op      the binary operator applied to the intermediate state and the message
   * @return        input with intermediate results
   */
  def smap[S, B: Message](z: S)(fsm: (S, A) => (S, B)): IChan[B] =
    add(ichan.immutable.StateT(1, z, fsm))

  /**
   * Zips a stream with its indices.
   *
   *
   * @return A new stream containing pairs consisting of all elements of this
   * stream paired with their index. Indices start at `0`.
   *
   * @usecase def zipWithIndex: IChan[(A, Long)]
   *
   * @return A new stream containing pairs consisting of all elements of this
   * stream paired with their index. Indices start at `0`.
   *
   * @example
   * `IChan("a", "b", "c").zipWithIndex = IChan(("a", 0), ("b", 1), ("c", 2))`
   */
  def zipWithIndex(implicit ma: Message[A]): IChan[(A, Long)] = smap(0l)((i, a) => (i + 1, (a, i)))

  /**
   * Zips a stream with its indices.
   *
   *
   * @return A new stream containing pairs consisting of all elements of this
   * stream paired with their index. Indices start at `0`.
   *
   * @usecase def zipWithBigIndex: IChan[(A, BigDecimal)]
   *
   * @return A new stream containing pairs consisting of all elements of this
   * stream paired with their index. Indices start at `0`.
   *
   * @example
   * `IChan("a", "b", "c").zipWithIndex = IChan(("a", 0), ("b", 1), ("c", 2))`
   */
  def zipWithBigIndex(implicit ma: Message[A]): IChan[(A, BigDecimal)] = smap(BigDecimal(0))((i, a) => (i + 1, (a, i)))

  /**
   * Produces an input resulting from applying a repeatedly a parser combinator to this input stream.
   *
   * @tparam B      the type of the messages in the resulting input
   * @tparam C      the type of the messages parsed
   * @param parser  a parser combinator
   * @return        input with parsed results
   */
  def parse[C >: A: Message, B: Message](parse: parsing.Parser[C, B]): IChan[B] =
    add(ichan.immutable.ParserT(1, parse, parse))

  /**
   * Produces a collection containing cummulative results of applying the operator going first to last message.
   *
   * @tparam B      the type of the messages in the resulting input
   * @param z       the initial value
   * @param op      the binary operator applied to the intermediate result and the message
   * @return        input with intermediate results
   */
  def scan[B: Message](z: B)(f: (B, A) => B): IChan[B] =
    add(ichan.immutable.ScannerT(z, f))

  /**
   * Operator equivalent to flatMap
   *
   */
  def >>\[B](f: (A, IChan[A]) => IChan[B])(implicit ma: Message[A], mb: Message[B]): IChan[B] =
    flatMap(f)

  /**
   * Builds a new input by applying a function to the first message in a stream and
   * the seed of remaining stream. The new input will then behave like the stream returned by
   * that function.
   *
   *  @param f      the function.
   *  @tparam B     the message type of the returned input channel.
   *  @return       a new input channel resulting from applying the function `f`.
   *
   *  @usecase def flatMap[B](f:(A, IChan[A]) => IChan[B]):IChan[B]
   *
   *  @return       a new segment resulting from applying the given collection-valued function
   *                `f` to each message of this segment and concatenating the results.
   */
  def flatMap[B](f: (A, IChan[A]) => IChan[B])(implicit ma: Message[A], mb: Message[B]): IChan[B] =
    new IChan[B] {

      def complexity = outer.complexity

      def read(thread: UThread,
        k: (Seg[B], IChan[B]) => Unit): Unit =
        outer.step(thread) {
          f(_, _) match {
            case nil: ichan.NilIChan =>
              k(Seg(), nil)
            case next =>
              next.read(thread, k)
          }
        } { signal => k(Seg(), IChan.empty(signal)) }

      def poison(signal: Signal): Unit = outer.poison(signal)

      def add[C: Message](transformer: IChan[B] => IChan[C]): IChan[C] =
        transformer(this)
    }

  /**
   * Builds a new collection by applying a function to all messages of this channel
   *  and concatenating the results.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned collection.
   *  @return       a new channel resulting from applying the given collection-valued function
   *                `f` to each message of this channel and concatenating the results.
   *
   *  @usecase def flatMapSeg[B](f: A => Seg[B]): Seg[B]
   *
   *  @return       a new segment resulting from applying the given collection-valued function
   *                `f` to each message of this channel and concatenating the results.
   */
  def flatMapSeg[B](f: A => Seg[B])(implicit ma: Message[A], mb: Message[B]): IChan[B] = {
    flatMap((a, next) => next match {
      case IChan(signal) =>
        f(a) ++: IChan.empty(signal)
      case ichan =>
        f(a) ++: ichan.flatMapSeg(f)
    })
  }

  /**
   * Builds a new input by applying a function to the first message parsed in a stream and
   * the seed of remaining stream. The new input will then behave like the stream reurned by
   * that function.
   *
   *  @param parser the parser used to parse the first message.
   *  @param f      the function.
   *  @tparam B     the element produced by the parser.
   *  @tparam D     the message type of the returned input channel.
   *  @return       a new input channel resulting from applying the function `f`.
   *
   *  @usecase def flatMap[B](f:(A, IChan[A]) => IChan[B]):IChan[B]
   *
   *  @return       a new segment resulting from applying the given collection-valued function
   *                `f` to each message of this segment and concatenating the results.
   */
  def flatMap[C >: A, B, D](parser: parsing.Parser[C, B], f: (B, IChan[C]) => IChan[D])(implicit mb: Message[C]): IChan[D] =
    _flatMapParse(Seg.empty, parser, f)

  private final def _flatMapParse[C >: A, B, D](trail: Seg[C], parser: parsing.Parser[C, B], f: (B, IChan[C]) => IChan[D])(implicit mb: Message[C]): IChan[D] =
    new IChan[D] {
      import parsing._

      def complexity = outer.complexity

      def read(thread: UThread,
        k: (Seg[D], IChan[D]) => Unit): Unit =
        outer.read(thread, { (seg, ichan) =>
          parser(seg) match {
            case Done(b, as) => f(b, as ++: ichan).read(thread, k)
            case partial: Partial[B, C] => ichan._flatMapParse(seg, partial.parser, f).read(thread, k)
            case fail: Fail =>
              trail.poison(fail)
              ichan.poison(fail)
              k(Seg(), IChan.empty(fail))
          }
        })

      def poison(signal: Signal): Unit = {
        trail.poison(signal)
        outer.poison(signal)
      }

      def add[E: Message](transformer: IChan[D] => IChan[E]): IChan[E] =
        transformer(this)
    }

  /**
   * Selects all messages of this input which satisfy a predicate.
   *
   *  @param p     the predicate used to test messages.
   *  @return      a new input consisting of all messages of this input that satisfy the given
   *               predicate `p`. The order of the messages is preserved.
   */
  def filter(p: A => Boolean)(implicit ma: Message[A]): IChan[A] =
    add(ichan.immutable.FilterT(1, p))

  /**
   * Builds a new input by applying a partial function to all messages of this input
   *  on which the function is defined.
   *
   *  @param pf     the partial function which filters and maps the stream elements.
   *  @tparam B     the message type of the returned input channel.
   *  @return       a new input resulting from applying the partial function
   *                `pf` to each message on which it is defined.
   *                The order of the messages is preserved.
   *
   *  @usecase def collect[B](pf: PartialFunction[A, B]): IChan[B]
   */
  def collect[B](f: PartialFunction[A, B])(implicit ma: Message[A], mb: Message[B]): IChan[B] =
    add(ichan.immutable.PartialMapperT(1, f))

  /**
   * Selects all elements except first ''n'' ones.
   *
   * @param n the number of elements to drop from this stream.
   * @return an input consisting of all elements of this input except the first `n` ones.
   */
  def drop(n: Int)(implicit ma: Message[A]): IChan[A] =
    add(ichan.immutable.DropT(n))

  /**
   * Skips longest sequence of elements of this input which satisfy given
   *  predicate `p`, and returns an input of the remaining elements.
   *
   *  @param p the predicate used to skip elements.
   *  @return  an input producing the remaining elements
   */
  def dropWhile(p: A => Boolean)(implicit ma: Message[A]): IChan[A] =
    add(ichan.immutable.DropWhileT(p))

  /**
   * Converts this input stream of traversable collections into
   *  an input stream in which all message collections are concatenated.
   *  @tparam B the type of the messages of each traversable collection.
   *  @param asTraversable an implicit conversion which asserts that the message type of this
   *         input channel is a `Traversable`.
   *  @param message an implicit message definition for the message type of the
   *         `Traversable`.
   *  @return a new input resulting from concatenating all the `Traversable` collections.
   *  @usecase def flatten[B]: IChan[B]
   */
  def flatten[B](implicit asTraversable: A => /*<:<!!!*/ Traversable[B], message: Message[B]): IChan[B] =
    add(ichan.immutable.SegMapperT[A, B](1, seg => seg.flatten))

  /**
   * Append a stream after this stream only if this stream terminates normally
   * with EOS.
   *
   * @param next the stream to append.
   * @return the appended stream.
   */
  def append[B >: A](next: => IChan[B]): IChan[B] =
    onSignal({ case EOS => next })

  /**
   * Append a stream after this stream if this stream terminates
   * with the signal for which the partial function is defined.
   *
   * @param next the partial function returning the stream to append
   *             if the signal matches the signal(s) for which it is defined.
   * @return the appended stream.
   */
  def onSignal[B >: A](next: PartialFunction[Signal, IChan[B]]): IChan[B] =
    ichan.CombineUtils.append(this, next)

  /**
   * Append a stream after this stream only if this stream terminates
   * abnormally with a signal that is not EOS.
   *
   * @param next the stream to append in case of an error signal.
   * @return the appended stream.
   */
  def onError[B >: A](next: => IChan[B]): IChan[B] =
    onSignal {
      case EOS => IChan.empty(EOS)
      case _ => next
    }

  /**
   * Returns an input which groups messages produced by this input
   * into fixed size blocks. The last group may contain less messages
   * the number of messages receives is not a multiple of the group
   * size.
   *
   *  @param size the size of the groups.
   *  @return  an input producing the groups
   */
  def grouped(size: Int)(implicit ma: Message[A]): IChan[Seg[A]] =
    add(ichan.immutable.GroupedT(size))

  /**
   * Create an input that produces first ''n'' messages of this input.
   *
   *  @param  n      the number of messages to take
   *  @param  tailK     a function that takes the remaining of the stream as argument if it is not terminated
   *  @return an input producing only of the first `n` messages of this input,
   *          or less than `n` messages if the input produces less messages.
   */
  def take[B >: A](n: Int, tailK: IChan[B] => IChan[B])(implicit ma: Message[B]): IChan[B] =
    add(ichan.immutable.TakeT(n, tailK))

  /**
   * Create an input that produces first ''n'' messages of this input.
   * The input stream is poisoned after the n messages have been consumed.
   *
   *  @param  n      the number of messages to take
   *  @return an input producing only of the first `n` messages of this input,
   *          or less than `n` messages if the input produces less messages.
   */
  def take(size: Int)(implicit ma: Message[A]): IChan[A] =
    add(ichan.immutable.TakeT(size, tail => { tail.poison(EOS); IChan.empty(EOS) }))

  /**
   * Create an input that produces messages of this input until an event occurs.
   *
   *  @param  splitter  a channel that delivers the message that will split the current channel
   *  @param  tailK     a function that takes the remaining of the stream as argument if it is not terminated.
   *                    tailK is invoked with the signal from the `splitter` if it is terminated.
   *  @return an input producing messages of this input until the `splitter` produces and event,
   *          or all the messages if the splitter never delivers an event.
   */
  def takeUntil[B >: A, C](splitter: IChan[C], tailK: Either[Signal, (C, IChan[C], IChan[B])] => IChan[B])(implicit ma: Message[B], mc: Message[C]): IChan[B] =
    add(ichan.immutable.TakeUntilT(splitter, tailK))

  /**
   * Create an input that produces messages of this input until an event occurs.
   * The input stream is poisoned after the n messages have been consumed.
   *
   *  @param  splitter  a channel that delivers the message that will split the current channel.
   *  @return an input producing messages of this input until the `splitter` produces and event,
   *          or all the messages if the splitter never delivers an event.
   */
  def takeUntil[B](splitter: IChan[B])(implicit ma: Message[A], mb: Message[B]): IChan[A] =
    add(ichan.immutable.TakeUntilT[A, B](splitter, {
      case Left(signal) => IChan.empty(EOS)
      case Right((b, splitter, tail)) =>
        mb.poison(b, EOS)
        splitter.poison(EOS)
        tail.poison(EOS)
        IChan.empty(EOS)
    }))

  /**
   * Create an input that produces longest sequence of this input that satisfy
   * predicate `p`.
   *
   *  @param  p    the predicate to satisfy
   *  @param  tailK  a function that takes the remaining of the stream as argument
   *  @return an input producing only of the first messages that satisfy
   *          the predicate.
   */
  def span[B >: A](p: B => Boolean, tailK: IChan[B] => IChan[B])(implicit ma: Message[B]): IChan[B] =
    add(ichan.immutable.SpanT(1, p, tailK))

  /**
   * Create an input that produces longest sequence of this input that satisfy
   * predicate `p`.
   * The input stream is poisoned after the the conforming messages have
   * been consumed.
   *
   *  @param  p    the predicate to satisfy
   *  @return an input producing only of the first messages that satisfy
   *          the predicate.
   */
  def span(p: A => Boolean)(implicit ma: Message[A]): IChan[A] =
    add(ichan.immutable.SpanT(1, p, tail => { tail.poison(EOS); IChan.empty(EOS) }))

  def testable(implicit ma: Message[A]): ichan.TestableIChan[A] =
    ichan.mutable.TesterT().apply(this)

  import molecule.process.Process

  /**
   * Builds a new input by applying a function that returns a process
   *  to all messages of this input.
   *
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned input.
   *  @return       a new input resulting from applying the given function
   *                `f` to each message of this input.
   *
   *  @usecase def mapP[B](f: A => Process[B]): IChan[B]
   */
  def mapP[B](f: A => Process[B])(implicit ma: Message[A], mb: Message[B]): IChan[B] = new IChan[B] {

    def complexity = 1

    def read(t: UThread,
      k: (Seg[B], IChan[B]) => Unit): Unit =
      outer.step(t) { (a, nichan) =>
        val ri = t.platform.launch(f(a))
        ri.read(a => t.submit(k(Seg(a), nichan.mapP(f))), signal => t.submit(k(Seg(), IChan.empty(signal))))
      } { signal => k(Seg(), IChan.empty(signal)) }

    def poison(signal: Signal): Unit = outer.poison(signal)

    def add[C: Message](transformer: IChan[B] => IChan[C]): IChan[C] =
      transformer(this)
  }

  /**
   * Applies a binary operator to a start value and all messages of this channel,
   *  going left to right.
   *
   *  @param   z    the start value.
   *  @param   op   the binary operator.
   *  @tparam  B    the result type of the binary operator.
   *  @return  the result of inserting `op` between consecutive messages of this channel,
   *           going left to right with the start value `z` on the left:
   *           {{{
   *             op(...op(z, x,,1,,), x,,2,,, ..., x,,n,,)
   *           }}}
   *           where `x,,1,,, ..., x,,n,,` are the messages of this segment.
   */
  def fold[B: Message](z: B)(op: (B, A) => B): Process[B] =
    process.core.Fold(z)(op)(this)

  //  def groupBy[K](f: A => K)(implicit ma:Message[A]):Process[collection.immutable.Map[K, Seg[A]]] = {
  //    import scala.collection.immutable.Map
  //    fold(Map.empty[K, Seg[A]])({(m, a) =>
  //      val k = f(a)
  //      val s = m.getOrElse(k, Seg()) 
  //      m + (k -> (s :+ a))
  //    })(Message.impure((map, signal) => map.values.foreach(_.poison(signal))))
  //  }

  /**
   * Apply sequentially a side effectful function to each message in the
   * stream.
   *
   *  @param  f the effectful function that will be applied to each element
   *            of the sequence
   *  @return stream whose result indicates the termination of the foreach
   *          operation.
   */
  def foreach[B](f: A => B): Process[Unit] =
    process.core.Foreach(f)(this)

  /**
   * Transfer the content of this input to an output. The
   * input/output will be closed with the same signal
   * as the output/input.
   *
   *  @param  output the output to connect to this input
   *  @return stream whose result indicates which channel was closed first
   *          (Left for ichan, Right for ochan)
   */
  def connect[B >: A](ochan: OChan[B]): Process[Either[Signal, Signal]] =
    process.core.Connect(this, ochan)

  /**
   * Forward asynchronously the content of this input to an output.
   * It stops as soon as one of the two channel is poisoned and it does
   * not propagate the poison signal.
   *
   *  @param  output the output to connect to this input
   *  @return A stream that returns the channels after the forward operation.
   *          If the stream completed successfully the input channel will be closed,
   *          otherwise the output channel is poisoned.
   */
  def forward[B >: A](ochan: OChan[B]): Process[(IChan[B], OChan[B])] =
    process.core.Forward(this, ochan)

  import ichan.CombineUtils

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
   * @param closeIfLeftClosed flag that indicating if the resulting channel must be closed if this
   *                          channel is closed.
   * @param closeIfRightClosed flag that indicating if the resulting channel must be closed if the other
   *                          channel is closed.
   * @return a new input channel that produces messages of type Either[A, B] for every
   * message produced on this or the other channel
   */
  def interleave[B](
    right: IChan[B], closeIfLeftClosed: Boolean = false, closeIfRightClosed: Boolean = false)(implicit ma: Message[A], mb: Message[B]): IChan[Either[A, B]] =
    CombineUtils.interleave(this, right, closeIfLeftClosed, closeIfRightClosed)

  /**
   * Join the messages produced asynchronously on this channel with the messages
   * produced on an other channel.
   * This builds a new input channel that produces tuples of type (A, B) each time a new message
   * of type A or B is available, where `A` is the type of messages produced
   * on this channel and `B` is the type of messages
   * produced on the other channel. The resulting channel is closed both
   * input channels are closed.
   *
   * @param right the other input channel
   * @return a new input channel that produces messages of type (A, B) for every
   * message produced on this or the other channel
   */
  def join[B](right: IChan[B])(implicit ma: Message[A], mb: Message[B]): IChan[(A, B)] =
    this.flatMap((a, left) =>
      right.flatMap((b, right) =>
        left.interleave(right).smap((a, b))((s, e) =>
          e.fold(
            a => {
              val ns = (a, s._2)
              (ns, ns)
            },
            b => {
              val ns = (s._1, b)
              (ns, ns)
            }
          )
        )
      ))

  /**
   * Merge the streams of two channels.
   * This builds a new input channel that produces messages from `this` channel and the other channel.
   *
   * @param right the other input channel
   * @return a new input channel that produces messages coming on both input channels
   */
  def merge[B >: A](right: IChan[B])(implicit mb: Message[B]): IChan[B] =
    CombineUtils.merge(this, right)

  /**
   * Monadic 'join' operator M[M[A]] => M[A]. Converts this stream of stream into
   *  an stream, which merges messages from all the sub-streams.
   *  @tparam B the type of the messages of sub-streams.
   *  @param asIChan an implicit conversion which asserts that the message type of this
   *         input channel is an `IChan`.
   *  @param ma an implicit message definition for the message type of this stream.
   *  @param mb an implicit message definition for the message type of the sub-stream.
   *  @return a new input resulting from merging all the sub-streams.
   *  @usecase def join[B]: IChan[B]
   */
  def join[B](implicit asIChan: A => /*<:<!!!*/ IChan[B], ma: Message[A], mb: Message[B]): IChan[B] =
    flatMap((a, next) => asIChan(a).merge(next.join))

  /**
   * Create an input stream by merging all the input streams created by applying the function `f`
   * to all the elements of this input stream.
   *  @tparam B the type of the messages of input streams created by `f`.
   *  @param f a function that creates new input streams from each element of this stream.
   *  @param ma an implicit message definition for the message type of this input.
   *  @param mb an implicit message definition for the message type of the new streams.
   *  @return a new input resulting from merging all the streams created by applying `f` to
   *  each elements of this stream.
   *  @usecase def mapMany[B](f:A => IChan[B]): IChan[B]
   */
  def mapMany[B](f: A => IChan[B])(implicit ma: Message[A], mb: Message[B]): IChan[B] =
    map(f).join

  /**
   * Zip and hence synchronize the streams of two channels.
   * This builds a new input channel that produces pair of messages from `this` channel and the other channel.
   *
   * @param right the other input channel
   * @return a new input channel that produces pairs of messages coming on both input channels
   */
  def zip[B](right: IChan[B])(implicit ma: Message[A], mb: Message[B]): IChan[(A, B)] =
    CombineUtils.ZipChan(this, right)

  //      val m = collection.mutable.Map.empty[K, Seg[A]]
  //      for (elem <- this) {
  //        val key = f(elem)
  //        val bldr = m.getOrElseUpdate(key, newBuilder)
  //        bldr += elem
  //      }
  //      val b = immutable.Map.newBuilder[K, Repr]
  //      for ((k, v) <- m)
  //        b += ((k, v.result))
  //
  //      b.result
  //  }
}

/**
 * Companion object for stream input channels
 */
object IChan {

  /**
   * Create a(n empty) channel closed with the given signal.
   *
   * @param signal the signal
   *
   * @return the channel closed with the given signal
   */
  def empty[A](signal: Signal): IChan[A] =
    ichan.NilIChan(signal)

  /**
   * Create a stream channel that delivers a single value.
   *
   * @param a the value
   *
   * @return the channel closed with the given signal
   */
  def apply[A: Message](a: A): IChan[A] =
    ichan.FrontIChan(ichan.BackIChan(channel.IChan(a)))

  /**
   * Extract a signal out of a closed channel.
   *
   * @param chan a channel
   * @return empty is the channel is not closed, else its signal.
   */
  def unapply(chan: IChan[_]): Option[Signal] =
    if (chan.isInstanceOf[ichan.NilIChan]) Some(chan.asInstanceOf[ichan.NilIChan].signal) else None

  private[this] val IChanIsPoisonlable = new Message[IChan[Any]] {
    def poison(message: IChan[Any], signal: Signal): Unit = {
      message.poison(signal)
    }
  }

  /**
   * Create a stream input channel from a system input channel.
   *
   * @param ch the system-level channel
   *
   * @return the stream channel
   */
  def lift[A: Message](ch: channel.IChan[A]): IChan[A] =
    ichan.FrontIChan(ichan.BackIChan(ch))

  /**
   * Lift Stream input channel factory to the system-level input channel factory.
   * This lift adds additional factory methods, which do return system-level
   * input channels, but the latter will be implicitly lifted to stream input channels.
   */
  @inline
  final implicit def liftIChanCompanion(o: IChan.type): channel.IChan.type = channel.IChan

  /**
   * Message typeclass for channels.
   *
   */
  implicit def ichanIsMessage[A]: Message[IChan[A]] = IChanIsPoisonlable

  private def liftV[A](a: A): IChan[A] = IChan(a)(PureMessage)
  private def liftC[A: Message](a: A): IChan[A] = IChan(a)

  /**
   * Implicit channel lifting for standard types
   */
  implicit def liftBool(b: Boolean): IChan[Boolean] = liftV(b)
  implicit def liftString(s: String): IChan[String] = liftV(s)
  implicit def liftByte(b: Byte): IChan[Byte] = liftV(b)
  implicit def liftChar(c: Char): IChan[Char] = liftV(c)
  implicit def liftShort(s: Short): IChan[Short] = liftV(s)
  implicit def liftInt(i: Int): IChan[Int] = liftV(i)
  implicit def liftLong(l: Long): IChan[Long] = liftV(l)
  implicit def liftDouble(d: Double): IChan[Double] = liftV(d)
  implicit def liftBigDecimal(b: BigDecimal): IChan[BigDecimal] = liftV(b)
  implicit def liftFloat(f: Float): IChan[Float] = liftV(f)

  implicit def liftOption[A: Message](o: Option[A]): IChan[Option[A]] = liftC(o)
  implicit def liftEither[A: Message, B: Message](e: Either[A, B]): IChan[Either[A, B]] = liftC(e)
  implicit def liftTraversable[CC[A] <: Traversable[A], A: Message](t: CC[A]): IChan[CC[A]] = liftC(t)

  implicit def liftTuple1[A: Message](p: Tuple1[A]): IChan[Tuple1[A]] = liftC(p)
  implicit def liftTuple2[A: Message, B: Message](p: Tuple2[A, B]): IChan[Tuple2[A, B]] = liftC(p)
  implicit def liftTuple3[A: Message, B: Message, C: Message](p: Tuple3[A, B, C]): IChan[Tuple3[A, B, C]] = liftC(p)
  implicit def liftTuple4[A: Message, B: Message, C: Message, D: Message](p: Tuple4[A, B, C, D]): IChan[Tuple4[A, B, C, D]] = liftC(p)
  implicit def liftTuple5[A: Message, B: Message, C: Message, D: Message, E: Message](p: Tuple5[A, B, C, D, E]): IChan[Tuple5[A, B, C, D, E]] = liftC(p)

}
