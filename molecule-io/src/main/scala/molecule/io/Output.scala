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
import stream.OChan
import stream.ochan.NilOChan
import platform.UThread

/**
 * A process-level output channel.
 *
 *  @tparam  A    the type of the output's messages
 *
 */
abstract class Output[-A] extends impl.Flushable {

  /**
   * Write a value to this output.
   *
   * @param a the value to write
   */
  def write(a: A): IO[Unit]

  /**
   * Ensure that channel is not flushing before applying a
   * transformation. This is only required if the process transforms
   * the channel after a write followed by another read operation.
   */
  def syncUpdate[B](f: Output[A] => Output[B]): IO[Output[B]]

  /**
   * Write a segment.
   *
   * @param seg the segment
   */
  def writeSeg(seg: Seg[A]): IO[Unit]

  /**
   * Write the content of an input to this output.
   * The method returns control when the EOS is reached or
   * raises the signal if it is different than EOS.
   *
   * @param input the input from which to forward the content
   */
  def write(input: Input[A]): IO[Unit] = flush(input) >> IO()

  /**
   * Forward the content of an input to this output.
   * The method returns control when the EOS is reached or
   * raises the signal if it is different than EOS.
   *
   * @param input the input from which to forward the content
   */
  @deprecated("Use `flush` instead", "0.1")
  def forward(input: Input[A]): IO[Signal] = flush(input)

  /**
   * Flush the content of an input to this output.
   * The method returns control when the EOS is reached or
   * raises the signal if it is different than EOS.
   *
   * @param input the input from which to forward the content
   */
  def flush(input: Input[A]): IO[Signal]

  /**
   * Forward the content of a stream input to this output.
   * The method returns control when the EOS is reached or
   * raises the signal if it is different than EOS.
   *
   * It raises an exception if the output is poisoned before
   * the end of stream has been reached. In this case the input
   * is not poisoned.
   *
   * @param input the input from which to forward the content
   */
  @deprecated("Use `flush` instead", "0.1")
  def forward(ichan: IChan[A]): IO[Signal] = flush(ichan)

  /**
   * Flush the content of a stream input to this output.
   * The method returns control when the EOS is reached or
   * raises the signal if it is different than EOS.
   *
   * It raises an exception and poisons the input stream if the
   * output is poisoned before the end of stream has been reached.
   *
   * @param input the input from which to forward the content
   */
  def flush(ichan: IChan[A]): IO[Signal]

  import channel.RIChan

  /**
   * Connect an input to this output in a separate process.
   * Both process-level channels are released and the method returns control
   * immediately. The signal terminating the input channel
   * will be also copied to the output channel.
   *
   * @param input the input to connect
   */
  def connect(input: Input[A]): IO[RIChan[Either[Signal, Signal]]] =
    input.connect(this)

  /**
   * Connect an input to this output. Both process-level
   * channels are released and the method returns control
   * immediately. The signal terminating the input channel
   * will be also copied to the output channel.
   *
   * @param ichan the input to connect
   */
  def connect(ichan: IChan[A]): IO[RIChan[Either[Signal, Signal]]] =
    release() >>\ { ochan =>
      IO.launch(ichan connect ochan)
    }

  /**
   * Close this output
   */
  def close(): IO[Unit] = close(EOS)

  /**
   * Close this output with a given signal.
   *
   * @param signal the signal
   */
  def close(signal: Signal): IO[Unit]

  /**
   * Release this output from the process.
   *
   * @param signal the signal
   */
  def release(): IO[OChan[A]]

  /**
   * Builds a new output by applying a function to all the messages
   * wrote to it.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned input.
   *  @return       a new input resulting from applying the given function
   *                `f` to each message of this input.
   *
   *  @usecase def map[B](f: B => A): Output[B]
   */
  def map[B: Message](f: B => A): Output[B]

  /**
   * Builds a new debugging output that prints every message received.
   *
   *  @param label  the label to put in front of each debug line.
   *  @param f      A function converting messages to a string (defaults to _.toString).
   *  @return       The same output excepted each message will be printed.
   */
  def debug[B <: A: Message](label: String, f: B => String = { b: B => b.toString }): Output[B]

  /**
   * Builds a new output that applies a function to all messages written
   *  to it and concatenates the results.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned output.
   *  @return       a new output resulting from applying the given collection-valued function
   *                `f` to each message written and concatenating the results.
   *
   *  @usecase def flatMap[B](f: B => Seg[A]): Input[B]
   *
   *  @return       a new input resulting from applying the given collection-valued function
   *                `f` to each message of this input and concatenating the results.
   */
  def flatMap[B: Message](f: B => Seg[A]): Output[B]

  /**
   * Builds a new output that applies a function to all messages written
   *  to it and concatenates the resulting segments.
   *
   *  @param f      the function to apply to each message.
   *  @tparam B     the message type of the returned output.
   *  @return       a new output resulting from applying the given collection-valued function
   *                `f` to each message written and concatenating the results.
   *
   *  @usecase def encode[B](f: B => Traversable[A]): Input[B]
   *
   *  @return       a new input resulting from applying the given collection-valued function
   *                `f` to each message of this input and concatenating the results.
   */
  def encode[B: Message](f: B => Seg[A]): Output[B]

  /**
   * Builds an output producing cummulative results of applying the operator going
   * from first to last message written.
   *
   * @tparam B      the type of the messages accepted by the resulting output
   * @param z       the initial state
   * @param op      the binary operator applied to the intermediate state and the message
   * @return        output producing intermediate results
   */
  def smap[S, B: Message](z: S)(fsm: (S, B) => (S, A)): Output[B]

}

object Output {

  import impl.{ UThreadContext, Promise }

  // Max Buffer Size: only need for processes that generate messages out of the blue
  // see io.examples.misc.InefficientGenerator
  @inline private final def MBS = 50 //platform.Platform.segmentSizeThreshold <- 5% performance penalty in thread ring

  private case object OutputReleasedSignal extends Signal
  private case object OutputOutdatedSignal extends Signal
  private case object OutputBusySignal extends Signal

  private[io] def apply[A: Message](master: UThreadContext, id: Int, ochan: OChan[A]): Output[A] = {
    if (ochan.isInstanceOf[NilOChan[_]])
      new OutputImpl[A](id, null, null, ochan, platform.Platform.segmentSizeThreshold)
    else {
      val ref = new MResourceRef(null)
      val output = new OutputImpl[A](id, master, ref, ochan, platform.Platform.segmentSizeThreshold)
      ref.reset(output)
      master.context.add(ref)
      output
    }
  }

  import scala.collection.mutable.Queue

  private[this]type K = Unit => Unit
  private[this]type H = Signal => Unit

  // master should be a 'val' but because of:
  // https://issues.scala-lang.org/browse/SI-5367
  // we must nullify it manually to release memory
  // when free outputs are unduly captured by closures.
  private final class OutputImpl[A: Message](
      val id: Int,
      private[this] final val master: UThreadContext,
      private[this] final var ref: MResourceRef,
      private[this] final var cnx: OChan[A],
      private[this] final val sst: Int) extends Output[A] with Resource {

    private[this] final var buffer: Seg[A] = Seg.empty[A]

    private[this] final def takeSeg(): Seg[A] = {
      val seg = buffer
      buffer = Seg.empty[A]
      seg
    }

    // Order doesn't matter
    final class SuspendedTask(thread: UThreadContext, task: => Unit, val next: SuspendedTask) {
      def resume() = thread.resume(task)
    }

    private[this] final var suspended: SuspendedTask = null

    def suspend(t: UThreadContext, task: => Unit): Unit = {
      val head = new SuspendedTask(t, task, suspended)
      suspended = head
    }

    private[this] final var flushing: Boolean = false

    def syncUpdate[B](f: Output[A] => Output[B]): IO[Output[B]] = new IO[Output[B]]({ (t, k) =>
      if (flushing)
        suspend(t, k(f(this)))
      else
        k(f(this))
    })

    def update[B: Message](f: OChan[A] => OChan[B]): Output[B] =
      update(OutputOutdatedSignal, f)

    def update[B: Message](reason: Signal, f: OChan[A] => OChan[B]): Output[B] = {
      if (flushing)
        throw new Error("Cannot update the ouput channel while it is being flushed. " +
          "Use sync() before applying a transformation to ensure that it is flushed.")

      (takeSeg() :+: cnx) match {
        case nil @ OChan(signal) =>
          signal match {
            case OutputReleasedSignal | OutputOutdatedSignal | OutputBusySignal =>
              throw new Error("Output channel not available:" + signal)
            case _ =>
              f(nil) match {
                case nil: NilOChan[_] =>
                  new OutputImpl[B](id, master, null, nil, sst)
                case nochan =>
                  val out = Output(master, id, nochan)
                  master.activity.setDirty(out) // It may have buffered something
                  out
              }
          }
        case ochan =>
          cnx = OChan(reason)
          f(ochan) match {
            case nil @ OChan(signal) =>
              terminate(signal)
              new OutputImpl[B](id, master, null, nil, sst)
            case nochan =>
              val out = new OutputImpl(id, master, ref, nochan, sst)
              val process = master.activity
              if (process.isDirty(this)) {
                process.unsetDirty(this)
                process.setDirty(out)
              }
              ref.reset(out)
              out
          }
      }
    }

    // Invoked when the user-level thread yields
    def flush(uthread: UThread): Unit =
      if (!flushing && !cnx.isInstanceOf[NilOChan[_]]) {
        flushing = true
        val seg = takeSeg()
        cnx.write(uthread, seg, None, { nchan =>
          flushing = false
          restore(nchan)
          resumeAfterFlush()
        })
      }

    def restore(ochan: OChan[A]) {
      ochan match {
        case OChan(signal) =>
          terminate(signal)
        case _ =>
          cnx = ochan
      }
    }

    /**
     * Resume tasks that where suspended while flushing
     */
    private def resumeAfterFlush(): Unit = {
      var copy = suspended
      suspended = null
      while (copy != null) {
        copy.resume()
        copy = copy.next
      }
    }

    /**
     * Method called when ochan is closed, terminated or released.
     * Any segment not already flushed will be poisoned.
     */
    private def terminate(signal: Signal) {
      if (!cnx.isInstanceOf[NilOChan[_]]) {
        val seg = takeSeg()
        seg.poison(signal)

        master.activity.unsetDirty(this)
        cnx = OChan(signal)
        master.context.remove(ref)
        ref.reset(null)
        // SI-5367
        ref = null
      }
    }

    def shutdown(signal: Signal): Unit = {
      if (flushing)
        suspend(master, close_!(master, signal, utils.NOOP))
      else close_!(master, signal, utils.NOOP)
    }

    def close(signal: Signal): IO[Unit] = new IO[Unit]({ (t, k) =>
      if (flushing)
        suspend(t, close_!(t, signal, k))
      else close_!(t, signal, k)
    })

    def close_!(t: UThreadContext, signal: Signal, k: Unit => Unit) {
      cnx match {
        case nil: NilOChan[_] =>
          nil.signal match {
            case OutputReleasedSignal | OutputOutdatedSignal =>
              t.fatal(Signal("Cannot close output. Output object is invalid because: " + nil.signal))
            case _ =>
            // Normal close, nothing to do here just continue
          }
        case ochan =>
          t.activity.unsetDirty(this)
          ochan.close(t.uthread, takeSeg(), signal)
          terminate(signal)
      }
      k()
    }

    def write(a: A): IO[Unit] = new IO[Unit]({ (t, k) =>
      if (flushing)
        suspend(t, buffer_!(t, a, k))
      else buffer_!(t, a, k)
    })

    private[this] def buffer_!(t: UThreadContext, a: A, k: Unit => Unit): Unit = {
      // Hot spot optimisation
      if (cnx.isInstanceOf[NilOChan[_]]) {
        val signal = cnx.asInstanceOf[NilOChan[_]].signal
        Message[A].poison(a, signal)
        t.raise(signal)
      } else {
        val length = buffer.length
        if (length == 0) {
          t.activity.setDirty(this)
        }

        if (length < sst) {
          buffer :+= a
          k()
        } else
          suspend(t, buffer_!(t, a, k))

      }
    }

    def writeSeg(seg: Seg[A]): IO[Unit] = new IO[Unit]({ (t, k) =>
      if (flushing)
        suspend(t, bufferSeg_!(t, seg, k))
      else bufferSeg_!(t, seg, k)
    })

    private[this] def bufferSeg_!(t: UThreadContext, seg: Seg[A], k: K): Unit =
      cnx match {
        case OChan(signal) =>
          seg.poison(signal)
          t.raise(signal)
        case ochan =>
          if (buffer.length == 0) {
            t.activity.setDirty(this)
            cnx = seg :+: ochan
          } else {
            cnx = takeSeg() :+: ochan
            buffer = seg
          }
          k()
      }

    def flush(input: Input[A]): IO[Signal] =
      new IO[Signal]({ (t, k) =>
        if (flushing)
          suspend(t, flushInput_!(t, input, k))
        else flushInput_!(t, input, k)
      })

    // Optimized stuff
    private[this] def flushInput_!(t: UThreadContext, input: Input[A], k: H): Unit = {
      // if (flushing)
      //  t.fatal(Signal("Assert: flushing must be false"))

      def kLastWrite(ochan: OChan[A]): (Seg[A], Signal) => Unit = { (seg, signal) =>
        if (seg.isEmpty) {
          restore(ochan)
          k(signal)
        } else {
          ochan.write(t, seg, None, { nochan =>
            restore(nochan)
            k(signal)
          })
        }
      }

      def kWrite(ochan: OChan[A]): Seg[A] => Unit = { seg =>
        ochan.write(t, seg, None, kCont)
      }

      lazy val kCont: OChan[A] => Unit = {
        case OChan(signal) =>
          terminate(signal)
          t.raise(signal)
        case ochan =>
          loop(ochan)
      }

      def loop(ochan: OChan[A]): Unit = {
        input.extractSeg(t, kWrite(ochan), kLastWrite(ochan))
      }

      cnx match {
        case OChan(signal) =>
          t.raise(signal)
        case ochan =>
          // Important: disable auto-flush because we do it manually. Otherwise, a kWrite may
          // be triggered in the middle of a flush.
          t.activity.unsetDirty(this)
          val ochan = takeSeg() :+: cnx
          cnx = OChan(OutputBusySignal)
          loop(ochan)
      }
    }

    def flush(ichan: IChan[A]): IO[Signal] = new IO[Signal]({ (t, k) =>
      if (flushing)
        suspend(t, flushStream_!(t, ichan, k))
      else flushStream_!(t, ichan, k)
    })

    private[this] def flushStream_!(t: UThreadContext, ichan: IChan[A], k: H): Unit = {

      def loop(ichan: IChan[A]): Unit = {
        ichan.read(t, { (seg, nichan) =>
          cnx.write(t, seg, None, {
            case OChan(signal) =>
              terminate(signal)
              nichan match {
                case IChan(signal) =>
                  k(signal)
                case _ =>
                  nichan.poison(signal)
                  t.raise(signal)
              }
            case nochan =>
              cnx = nochan
              nichan match {
                case IChan(signal) =>
                  k(signal)
                case nichan =>
                  loop(nichan)
              }
          })
        })
      }

      ichan match {
        case IChan(signal) => k(signal)
        case _ =>
          cnx match {
            case OChan(signal) =>
              ichan.poison(signal)
              t.raise(signal)
            case ochan =>
              cnx = takeSeg() :+: ochan
              loop(ichan)
          }
      }
    }

    def release(): IO[OChan[A]] = new IO[OChan[A]]({ (t, k) =>
      if (flushing)
        suspend(t, release_!(t, k))
      else release_!(t, k)
    })

    private[this] def release_!(t: UThreadContext, k: OChan[A] => Unit): Unit = {
      cnx match {
        case nil @ OChan(signal) =>
          signal match {
            case OutputReleasedSignal | OutputOutdatedSignal | OutputBusySignal =>
              t.raise(signal)
            case _ =>
              k(nil)
          }
        case ochan =>
          if (t.activity.isDirty(this)) {
            // We flush before releasing
            val seg = takeSeg()
            terminate(OutputReleasedSignal)
            ochan.write(t, seg, None, k)
          } else {
            terminate(OutputReleasedSignal)
            k(ochan)
          }
      }
    }

    def map[B: Message](f: B => A): Output[B] =
      update(_.map(f))

    def debug[B <: A: Message](label: String, f: B => String = { b: B => b.toString }): Output[B] =
      update(_.debug(label, f))

    def flatMap[B: Message](f: B => Seg[A]): Output[B] =
      update(_.flatMap(f))

    def encode[B: Message](f: B => Seg[A]): Output[B] =
      update(_.encode(f))

    def smap[S, B: Message](z: S)(fsm: (S, B) => (S, A)): Output[B] =
      update(_.smap(z)(fsm))
  }

}

