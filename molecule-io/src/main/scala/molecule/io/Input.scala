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
import stream.ichan.NilIChan

/**
 * A process-level input channel.
 *
 *  @tparam  A    the type of the input's messages
 *
 */
abstract class Input[+A] extends SInput[A] with RInput[A] {

  /**
   * Read a single value using a parser or raise the signal if the input is closed
   *
   *  @param parser the parser used to parse a single element.
   *  @tparam B the type of the pareser element
   *
   *  @return  the next element of this input.
   */
  def read[C >: A, B](parser: parsing.Parser[C, B]): IO[B]

  /**
   * Builds a new input by applying a function to all messages of this input.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned input.
   *  @return       a new input resulting from applying the given function
   *                `f` to each message of this input.
   *
   *  @usecase def map[B](f: A => B): Input[B]
   */
  def map[B: Message](f: A => B): Input[B]

  /**
   * Builds a new debugging input that prints every message received.
   *
   *  @param label  the label to put in front of each debug line.
   *  @param f      A function converting messages to a string.
   *  @return       The same input excepted each message will be printed.
   *
   *  @usecase def debug[B](label:String, f: A => B): Input[B]
   */
  def debug(label: String, f: A => String = _.toString): Input[A]

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
   *  @usecase def collect[B](pf: PartialFunction[A, B]): Input[B]
   */
  def collect[B: Message](pf: PartialFunction[A, B]): Input[B]

  /**
   * Builds a new input by applying a function to all messages of this input
   *  and concatenating the results.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned input.
   *  @return       a new input resulting from applying the given collection-valued function
   *                `f` to each message of this input and concatenating the results.
   *
   *  @usecase def flatMap[B](f: A => Seg[B]): Input[B]
   *
   *  @return       a new input resulting from applying the given collection-valued function
   *                `f` to each message of this input and concatenating the results.
   */
  def flatMap[B: Message](f: A => Seg[B]): Input[B]

  /**
   * Produces an input containing cummulative results of applying the operator going first to last message.
   *
   * @tparam B      the type of the messages in the resulting input
   * @param z       the initial state
   * @param op      the binary operator applied to the intermediate state and the message
   * @return        input with intermediate results
   */
  def smap[S, B: Message](z: S)(fsm: (S, A) => (S, B)): Input[B]

  /**
   * Produces an input resulting from applying a repeatedly a parser combinator to this input stream.
   *
   * @tparam B      the type of the messages in the resulting input
   * @tparam C      the type of the messages parsed
   * @param parser  a parser combinator
   * @return        input with parsed results
   */
  def parse[C >: A: Message, B: Message](parser: parsing.Parser[C, B]): Input[B]

  /**
   * Produces a collection containing cummulative results of applying the operator going
   * first to last message.
   *
   * @tparam B      the type of the messages in the resulting input
   * @param z       the initial value
   * @param op      the binary operator applied to the intermediate result and the message
   * @return        input with intermediate results
   */
  def scan[B: Message](z: B)(op: (B, A) => B): Input[B]

  /**
   * Builds a new input that compresses the content of each segment into a single value.
   *
   *  @param f      A function converting segments to a single value.
   *  @return       The compressed input.
   */
  def compress[B: Message](f: seg.Seg[A] => B): Input[B]

  /**
   * Selects all messages of this input which satisfy a predicate.
   *
   *  @param p     the predicate used to test messages.
   *  @return      a new input consisting of all messages of this input that satisfy the given
   *               predicate `p`. The order of the messages is preserved.
   */
  def filter(p: A => Boolean): Input[A]

  /**
   * Converts this input stream of traversable collections into
   *  an input stream in which all message collections are concatenated.
   *  @tparam B the type of the messages of each traversable collection.
   *  @param asTraversable an implicit conversion which asserts that the message type of this
   *         input is a `Traversable`.
   *  @param message an implicit message definition for the message type of the
   *         `Traversable`.
   *  @return a new input resulting from concatenating all the `Traversable` collections.
   *  @usecase def flatten[B]: Input[B]
   */
  def flatten[B](implicit message: Message[B], asTraversable: A => /*<:<!!!*/ Traversable[B]): Input[B]

  /**
   * Prepend a message in front of this input.
   *  @param x the message to prepend.
   *  @return  an input which produces `x` as first message and
   *           which continues with the remaining of the stream.
   *  @usecase def +:(x: A): Input[A]
   */
  def +:[B >: A: Message](x: B): Input[B]

  /**
   * Prepend the messages of a given segment in front of this input.
   *  @param prefix  The segment to prepend.
   *  @return  an input which produces that segment first and
   *           then continues with the remaining of the stream.
   *  @usecase def ++:(prefix: Seg[A]): Input[A]
   */
  def ++:[B >: A: Message](prefix: Seg[B]): Input[B]

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
  def dropWhile(p: A => Boolean): Input[A]

  /**
   * Returns an input which groups messages produced by this input
   * into fixed size blocks. The last group may contain less messages
   * the number of messages receives is not a multiple of the group
   * size.
   *
   *  @param size the size of the groups.
   *  @return  an input producing the groups
   */
  def grouped(size: Int): Input[Seg[A]]

  /**
   * Forward the content of this input to an output. It returns when this input is
   * empty but does not close the output.
   *
   *  @param  output the output on which to forward this content
   *  @return the signal that closes this input
   */
  @deprecated("Use `flush` instead", "1.0")
  def forward(output: Output[A]): IO[Signal] =
    flush(output)

  /**
   * Flush the content of this input to an output. It returns when this input is
   * empty but does not close the output.
   *
   *  @param  output the output on which to forward this content
   *  @return the signal that closes this input
   */
  def flush(output: Output[A]): IO[Signal] =
    output.flush(this)
}

private[io] object Input {

  import impl.{ UThreadContext, Promise }

  private object InputReleasedSignal extends Signal
  private object InputOutdatedSignal extends Signal
  private object InputBusySignal extends Signal

  def apply[A: Message](master: UThreadContext, id: Int, ichan: IChan[A]): Input[A] = {
    if (ichan.isInstanceOf[NilIChan])
      new InputImpl[A](id, null, null, ichan)
    else {
      val ref = new MResourceRef(null)
      val input = new InputImpl[A](id, master, ref, ichan)
      ref.reset(input)
      master.context.add(ref)
      input
    }
  }

  // The fields 'master' and 'ref' should be 'val' but because of:
  // https://issues.scala-lang.org/browse/SI-5367
  // we must nullify them manually to release memory
  // when free inputs are unduly captured by closures.
  private[io] final class InputImpl[A: Message](
      val id: Int,
      private[this] final var master: UThreadContext,
      private[this] final var ref: MResourceRef,
      private[this] final var cnx: IChan[A]) extends Input[A] with Resource {

    def signal = cnx match {
      case IChan(signal) => Some(signal)
      case _ => None
    }

    /**
     * Transform this input into another input
     * @param reason signal that must be set on former input interface.
     *        It will be raise if someone attempts to read the former updated input.
     * @param isTransiant indicates if the update is temporary, in which case
     *        we should not clear resources acquired or referencing this Input.
     * @param f the transformer.
     */
    private def update[B: Message](reason: Signal, f: IChan[A] => IChan[B], isTransiant: Boolean): Input[B] = {
      cnx match {
        case IChan(signal) =>
          signal match {
            case InputReleasedSignal | InputOutdatedSignal | InputBusySignal =>
              sys.error("Input channel not available:" + signal)
            case _ =>
              // https://issues.scala-lang.org/browse/SI-5367
              new InputImpl[B](id, null, null, f(cnx))
          }
        case ichan =>
          cnx = IChan.empty(reason)
          f(ichan) match {
            case nil: NilIChan =>
              terminate(nil)
              // https://issues.scala-lang.org/browse/SI-5367
              new InputImpl[B](id, null, null, nil)
            case nichan =>
              if (isTransiant) {
                // Preserve resources because
                // this will be followed by a call to restore
                val in = Input(master, id, nichan)
                cnx = IChan.empty(InputBusySignal)
                in
              } else {
                // We are creating a new permanent input and can get rid 
                // of the former one.
                val in = new InputImpl(id, master, ref, nichan)
                ref.reset(in)
                clear(NilIChan(InputOutdatedSignal))
                in
              }

          }
      }
    }

    private[this] def terminate(nil: NilIChan): Unit = {
      if (ref == null) System.err.println("Warning: Input has been closed twice!")
      else {
        ref.reset(null)
        master.context.remove(ref)
        clear(nil)
      }
    }

    /**
     * This reduces damage of SI-5367, because this Input might be captured
     *  by the closure of the transformer and won't be garbage collected (e.g. prime sieve).
     */
    private[this] def clear(nil: NilIChan): Unit = {
      cnx = nil
      // https://issues.scala-lang.org/browse/SI-5367
      master = null
      ref = null
    }

    /**
     * Most transformations are like this.
     */
    def update[B: Message](f: IChan[A] => IChan[B]): Input[B] =
      update(InputOutdatedSignal, f, false)

    /**
     * This one is for temporary transformation like 'span'.
     * Contract: restore is called just before the temporary stream terminates.
     */
    def updateTemporarily[B >: A: Message](f: (IChan[A], IChan[A] => IChan[B]) => IChan[B]): Input[B] = {
      update(InputBusySignal, ichan => f(ichan, restore), true)
    }

    def restore: IChan[A] => IChan[Nothing] = {
      case nil: NilIChan =>
        terminate(nil)
        IChan.empty(EOS)
      case nchan =>
        cnx = nchan
        ref.reset(this)
        IChan.empty(EOS)
    }

    def extractSeg(t: UThreadContext, k: Seg[A] => Unit, klast: (Seg[A], Signal) => Unit): Unit = {
      cnx.read(t,
        { (seg, next) =>
          next match {
            case nil @ NilIChan(signal) =>
              terminate(nil)
              klast(seg, signal)
            case ichan =>
              cnx = ichan
              k(seg)
          }
        })
    }

    def extract(t: UThreadContext, k: A => Unit, h: Signal => Unit): Unit = {
      cnx match {
        case IChan(signal) =>
          h(signal)
        case ichan =>
          ichan.step(t) { (a, next) =>
            next match {
              case nil: NilIChan =>
                terminate(nil)
                k(a)
              case ichan =>
                cnx = ichan
                k(a)
            }
          } { signal =>
            terminate(NilIChan(signal))
            h(signal)
          }
      }
    }

    import parsing.{ ParseResult, Parser, Done, Partial, Fail }

    private def extractParse[B](t: UThreadContext, parser: Parser[A, B], k: B => Unit, h: Signal => Unit) {
      cnx match {
        case IChan(signal) =>
          h(signal)
        case ichan =>
          def loop(ichan: IChan[A], parser: Parser[A, B]) {
            ichan.read(t, { (seg, ichan) =>
              val parseResult: Option[ParseResult[B, A]] = ichan match {
                case IChan(signal) => parser.result(seg, signal) match {
                  case None => None
                  case Some(Left(fail)) => Some(Fail(fail.name, "Stream was aborted. " + fail.msg))
                  case Some(Right(done)) => Some(done)
                }
                case _ => Some(parser(seg))
              }
              parseResult match {
                case None =>
                  cnx = ichan
                  h(EOS)
                case Some(Done(b, as)) =>
                  cnx = as ++: ichan
                  k(b)
                case Some(Partial(parser)) =>
                  loop(ichan, parser)
                case Some(fail: Fail) =>
                  cnx = ichan
                  h(fail)
              }
            })
          }
          loop(ichan, parser)
        //    	  ichan.flatMap(parser, {(b:B, ichan:IChan[A]) => cnx = ichan; k(b); ichan}).read(t, (seg, next)  
      }
    }

    def read[C >: A, B](parser: parsing.Parser[C, B]): IO[B] = new IO[B]({ (t, k) =>
      extractParse(t, parser.asInstanceOf[parsing.Parser[A, B]], k, t.raise) // safe but how to do it cleanly?
    })

    def shutdown(signal: Signal) =
      poison_!(signal)

    private def poison_!(signal: Signal): Unit = {
      if (!cnx.isInstanceOf[NilIChan]) {
        // Input has not been already poisoned
        cnx.poison(signal)
        terminate(NilIChan(signal))
      }
    }

    def poison(signal: Signal): IO[Unit] = new IO[Unit]({ (t, k) =>
      poison_!(signal)
      k(())
    })

    def poison(): IO[Unit] = poison(EOS)

    import platform.UThread

    def test(thread: UThread): Promise[WInput[A]] = new Promise[WInput[A]]({ k =>
      val t = cnx.testable
      cnx = t
      t.test(thread, _ => k(this))
    })

    def release(): IO[IChan[A]] = new IO[IChan[A]]({ (t, k) =>
      cnx match {
        case nil @ IChan(signal) =>
          signal match {
            case InputReleasedSignal | InputOutdatedSignal | InputBusySignal =>
              t.raise(signal)
            case _ =>
              k(nil)
          }
        case ichan =>
          terminate(NilIChan(InputReleasedSignal))
          k(ichan)
      }
    })

    import channel.RIChan

    def connect(output: Output[A]): IO[RIChan[Either[Signal, Signal]]] =
      output.release() >>\ connect

    def connect(ochan: stream.OChan[A]): IO[RIChan[Either[Signal, Signal]]] =
      release() >>\ { ichan =>
        IO.launch(ichan connect ochan)(PureMessage)
      }

    def map[B: Message](f: A => B): Input[B] =
      update(_.map(f))

    def compress[B: Message](f: Seg[A] => B): Input[B] =
      update(_.compress(f))

    def debug(label: String, f: A => String = _.toString): Input[A] =
      update(_.debug(label, f))

    def collect[B: Message](f: PartialFunction[A, B]): Input[B] =
      update(_.collect(f))

    def flatMap[B: Message](f: A => Seg[B]): Input[B] =
      update(_.flatMapSeg(f))

    def smap[S, B: Message](z: S)(f: (S, A) => (S, B)): Input[B] =
      update(_.smap(z)(f))

    def parse[C >: A: Message, B: Message](parser: parsing.Parser[C, B]): Input[B] =
      update(_.parse(parser))

    def scan[B: Message](z: B)(f: (B, A) => B): Input[B] =
      update(_.scan(z)(f))

    def filter(p: A => Boolean): Input[A] =
      update(_.filter(p))

    def grouped(size: Int): Input[Seg[A]] =
      update(_.grouped(size))

    def flatten[B](implicit message: Message[B], asTraversable: A => /*<:<!!!*/ Traversable[B]): Input[B] =
      update(_.flatten)

    def span(p: A => Boolean): Input[A] =
      updateTemporarily(_.span(p, _))

    import stream.liftIChan
    def fold[B: Message](z: B)(f: (B, A) => B): IO[B] = new IO[B]({ (t, k) =>
      val ichan = cnx
      cnx = IChan.empty(InputBusySignal)
      val stream = ichan.fold(z)(f)
      val (ri, ro) = channel.RChan.mk[B]()
      stream.start(t, ro)
      liftIChan(ri).read(t, {
        case (Seg(b), _) => k(b)
        case (_, IChan(signal)) => t.raise(signal)
      })
    })

    def take(size: Int): Input[A] =
      updateTemporarily(_.take(size, _))

    def dropWhile(p: A => Boolean): Input[A] = {
      cnx = cnx.dropWhile(p)
      this
    }

    def +:[B >: A: Message](b: B): Input[B] =
      update(b :: _)

    def ++:[B >: A: Message](t: Seg[B]): Input[B] =
      update(t ++: _)

    def interleave[B](right: RInput[B])(implicit mb: Message[B]): IO[Input[Either[A, B]]] = for {
      i1 <- this.release()
      i2 <- right.release()
      val ilvd = i1.interleave(i2)
      i <- IO.use(System.identityHashCode(ilvd), ilvd)
    } yield i

    def merge[B >: A: Message](right: RInput[B]): IO[Input[B]] = for {
      i1 <- this.release()
      i2 <- right.release()
      val merged = i1.merge(i2)
      i <- IO.use(System.identityHashCode(merged), merged)
    } yield i

  }

}