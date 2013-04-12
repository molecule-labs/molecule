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

import platform.UThread

/**
 * Input that produces one value at a time which may result from selecting
 * on multiple inputs.
 *
 */
abstract class WInput[+A] {

  private val _read: IO[A] = new IO[A]({ (t, k) =>
    extract(t, k, t.raise)
  })

  /**
   * Read a single value or raise the signal if the input is closed
   *
   *  @return  the next element of this input.
   */
  def read(): IO[A] = _read

  /**
   * Applies a function `f` to all elements of this input.
   *
   *  @param  f   the function that is applied for its side-effect to every element.
   *              The result of function `f` is discarded.
   *
   *  @tparam  U  the type parameter describing the result of function `f`.
   *              This result will always be ignored. Typically `U` is `Unit`,
   *              but this is not necessary.
   *
   *  @usecase def foreach(f: A => IO[Unit]): Unit
   */
  def foreach[U](f: A => IO[U]): IO[Unit] = new IO[Unit]({ (t, k) =>
    import impl.{ Context, Shutdown, Handover, UThreadContext }

    // Optimization
    val loopCtx = new Context()
    t.context.add(loopCtx)

    def cleanup(signal: Signal) = {
      t.context.remove(loopCtx)
      signal match {
        case s: Shutdown[_] => loopCtx.shutdown(EOS)
        case Handover => loopCtx.shutdown(EOS)
        case _ => loopCtx.shutdown(signal)
      }
    }

    val loopThread = new UThreadContext(
      t.activity,
      t.uthread, loopCtx,
      { signal =>
        cleanup(signal); t.raise(signal)
      },
      { signal =>
        cleanup(signal); t.fatal(signal)
      }
    )

    val done: Signal => Unit = {
      case EOS => cleanup(EOS); k(())
      case s => cleanup(s); t.raise(s)
    }

    lazy val apply: A => Unit = { a =>
      f(a).ask(loopThread, continue)
    }
    lazy val continue: Any => Unit = { _ =>
      loopCtx.reset(EOS); extract(loopThread, apply, done)
    }

    continue(())
  })

  /**
   * Create a selector that reads a value on either input unless
   * they are both closed. If both input are closed an SigInput
   * will be raised in the enclosing scope.
   */
  def <+>[B](right: WInput[B]): WInput[Either[A, B]] =
    WInput(this, false, right, false)

  /**
   * Create a selector that reads a value on either input unless
   * the left one is closed. If the left input is closed an SigInput
   * will be raised in the enclosing scope.
   */
  def <%+>[B](right: WInput[B]): WInput[Either[A, B]] =
    WInput(this, true, right, false)

  /**
   * Create a selector that reads a value on either input unless
   * the right one is closed. If the right input is closed an SigInput
   * will be raised in the enclosing scope.
   */
  def <+%>[B](right: WInput[B]): WInput[Either[A, B]] =
    WInput(this, false, right, true)

  /**
   * Create a selector that reads a value on either input unless
   *  one of them is closed. If the one of them is closed an SigInput
   * will be raised in the enclosing scope.
   */
  def <%+%>[B](right: WInput[B]): WInput[Either[A, B]] =
    WInput(this, true, right, true)

  /**
   * Tests whether this input is empty (or closed).
   *
   *  @return    `true` if the input contain no more elements, `false` otherwise.
   */
  def isEmpty: Boolean = signal.isDefined

  /**
   * Tests whether this input is closed and returns the signal.
   *
   *  @return  `Some(signal)` if the input contain no more elements, `None` otherwise.
   */
  def isClosed: Boolean = signal.isDefined

  /**
   * Returns some signal if the input is closed.
   *
   *  @return  `Some(signal)` if the input contain no more elements, `None` otherwise.
   */
  def signal: Option[Signal]

  /**
   * Test the input stream (see stream.TestableStream).
   */
  protected[io] def test(thread: UThread): impl.Promise[WInput[A]]

  protected[io] def extract(t: impl.UThreadContext, k: A => Unit, h: Signal => Unit)

}

private[io] object WInput {

  import impl.{ Promise, UThreadContext }

  def apply[A, B](left: WInput[A], closeIfLeftClosed: Boolean,
    right: WInput[B], closeIfRightClosed: Boolean): WInput[Either[A, B]] =
    new WInput[Either[A, B]] {

      def signal: Option[Signal] = {
        if (closeIfLeftClosed) {
          if (left.signal.isDefined)
            left.signal
          else if (closeIfRightClosed)
            right.signal
          else
            None
        } else if (closeIfRightClosed) {
          if (right.signal.isDefined)
            right.signal
          else
            None
        } else {
          // Both must be closed
          // Search for an error signal if this is the case
          right.signal match {
            case Some(EOS) =>
              left.signal
            case s: Some[_] =>
              s
            case None =>
              None
          }
        }
      }

      def test(thread: UThread): Promise[WInput[Either[A, B]]] = {
        Promise.either(left.test(thread), right.test(thread)).map(_ => this)
      }

      def extractRight(thread: UThreadContext, right: WInput[B],
        k: Either[A, B] => Unit,
        h: Signal => Unit): Unit =
        right.extract(thread, v => k(Right(v)), h)

      def extractLeft(thread: UThreadContext, left: WInput[A],
        k: Either[A, B] => Unit,
        h: Signal => Unit): Unit =
        left.extract(thread, v => k(Left(v)), h)

      def extract(thread: UThreadContext,
        k: Either[A, B] => Unit,
        h: Signal => Unit): Unit = {

        left.signal match {
          case Some(EOS) =>
            if (closeIfLeftClosed)
              h(EOS)
            else
              extractRight(thread, right, k, { signal =>
                if (closeIfRightClosed) h(signal)
                else extractLeft(thread, left, k, s => h(s))
              })
            return
          case Some(signal) => // error
            h(signal)
            return
          case _ =>
        }

        right.signal match {
          case Some(EOS) =>
            if (closeIfRightClosed)
              h(EOS)
            else
              extractLeft(thread, left, k, s => h(s))
            return
          case Some(signal) => // error
            h(signal)
            return
          case _ =>
        }

        // Optimization: since tests tasks do nothing, we schedule them on the low-level 
        // user-level thread directly and then reschedule the continuation of the winning 
        // one in the correct context. An alternative would be to implement buffering at 
        // input level and add a 'isBuffered' method to avoid testing input 
        // channel interfaces every time we test...
        Promise.either(left.test(thread.uthread),
          right.test(thread.uthread)).deliver { e =>
            thread.resume(e match {
              case Left(left) => // Closed inputs return synchronously from a test
                extractLeft(thread, left, k, {
                  case EOS =>
                    if (closeIfLeftClosed)
                      h(EOS)
                    else
                      extractRight(thread, right, k, s => h(s))
                  case signal =>
                    h(signal)
                })

              case Right(right) =>
                extractRight(thread, right, k, {
                  case EOS =>
                    if (closeIfRightClosed)
                      h(EOS)
                    else
                      extractLeft(thread, left, k, s => h(s))
                  case signal =>
                    h(signal)
                })
            })
          }
      }
    }
}