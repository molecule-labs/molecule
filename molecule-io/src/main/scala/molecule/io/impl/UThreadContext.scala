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
package io.impl

import platform.{ UThread, Platform }

private[io] class UThreadContext(
    val activity: Activity[_],
    val uthread: UThread, val context: Context,
    val raise: Signal => Unit,
    val fatal: Signal => Unit) extends UThread {

  def submit(task: => Unit): Unit =
    uthread.submit {
      resume(task)
    }

  def resume(task: => Unit): Unit =
    try {
      task
      activity.flush(uthread)
    } catch {
      case so: StackOverflowError =>
        System.err.println(
          """================= ERROR !!! ================
Uh oh, seems we have a StackOverflowError...
Please verify that your segments are not too large (e.g. reduce SST).
============================================""")
        fatal(Signal(so))
      case oom: OutOfMemoryError =>
        System.err.println(
          """================= ERROR !!! ================
Uh oh, seems we have a OutOfMemoryError...
Please verify that:
a) you don't perform tail calls in a for-comprehension (instead bind after the comprehension).
b) you don't allocate recursively exception handlers.
============================================""")
        fatal(Signal(oom))
      case t =>
        fatal(Signal(t))
    }

  def platform: Platform = uthread.platform

  def askOrHandle[A](
    ask: (UThreadContext, A => Unit) => Unit, k: A => Unit,
    handle: Signal => Unit,
    fatal: Signal => Unit = this.fatal): Unit = askOrHandle(this.context, new Context(), ask, k, handle, fatal)

  def askOrHandle[A](
    ctx: Context,
    ask: (UThreadContext, A => Unit) => Unit, k: A => Unit,
    handle: Signal => Unit,
    fatal: Signal => Unit): Unit = askOrHandle(this.context, ctx, ask, k, handle, fatal)

  def askOrHandle[A](
    parent: Context,
    ctx: Context,
    ask: (UThreadContext, A => Unit) => Unit, k: A => Unit,
    handle: Signal => Unit,
    fatal: Signal => Unit): Unit = {

    parent.add(ctx)

    def cleanup(signal: Signal) = {
      parent.remove(ctx)
      signal match {
        case s: Shutdown[_] => ctx.shutdown(EOS)
        case Handover => ctx.shutdown(EOS)
        case _ => ctx.shutdown(signal)
      }
    }

    // Cleanup closes input, which may raise a second signal.
    // We use this variable to guard against this condition
    var terminated = false

    val newThread = new UThreadContext(
      activity,
      uthread, ctx,
      { signal => // catchable path
        if (!terminated) { terminated = true; cleanup(signal); handle(signal) }
      },
      { signal => // fatal path
        if (!terminated) { terminated = true; cleanup(signal); fatal(signal) }
      }
    )

    ask(newThread, { a => // return path
      cleanup(EOS); k(a)
    })
  }
}