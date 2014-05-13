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

/**
 * Trait used to wraps task submitted to an external executor
 * inside monadic or streaming I/O.
 *
 * Do not forget to override shutdown and shutdownNow methods.
 *
 */
trait SysIO extends java.util.concurrent.Executor {

  /**
   * Create an that executes a thunk of code within this Executor (EXPERIMENTAL)
   *
   * @param thunk the thunk of code to execute.
   */
  def doIO[R](thunk: => R): IO[R] = new IO[R]({ (t, k) =>
    // double flip
    execute(new Runnable {
      def run =
        try {
          val r = thunk
          t.submit(k(r))
        } catch {
          case th: Throwable => t.fatal(Signal(th))
        }
    })
  })

  import channel.OChan

  /**
   * Create a system-level output channel that applies a function to each message written
   * to it within this Executor.
   *
   * @param thunk the (effectful) function applied to each message.
   */
  def newOChan[A: Message](f: A => Unit): OChan[A] = new OChan[A] { outer =>

    def write(seg: Seg[A], sigOpt: Option[Signal], k: OChan[A] => Unit): Unit =
      execute(new Runnable {
        def run =
          try {
            seg.foreach(f)
            k(outer)
          } catch {
            case th: Throwable => k(OChan[A](Signal(th)))
          }
      })

    def close(signal: Signal) = {}
  }

  /**
   * Create an action that executes a function that take the current
   * the continuation as argument. The function is executed within this
   * Executor and the current continuation is resumed within the
   * user-level thread executing the action.
   *
   * @param f the function that takes the current continuation as argument.
   * @return an action that return the value passed to the current continuation.
   */
  def callcc[R](thunk: (R => Unit) => Unit) = new IO[R]({ (t, k) =>
    execute(new Runnable {
      def run =
        try {
          val sk: R => Unit = r => t.submit(k(r))
          thunk(sk)
        } catch {
          case th: Throwable => t.raise(Signal(th))
        }
    })
  })
}