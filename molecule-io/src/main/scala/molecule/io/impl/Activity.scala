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

import molecule.{ process => proc }
import platform.UThread
import channel.ROChan

/**
 * Class that represents running processes.
 *
 */
private[io] class Activity[R](val ptype: proc.ProcessType, val rchan: ROChan[R]) {

  import scala.collection.immutable.HashSet

  override final def toString =
    ptype.name + "/" + System.identityHashCode(this)

  /**
   * Root context
   */
  private[io] final val rootCtx = new Context()

  val terminate: R => Unit = { r =>
    rootCtx.shutdown(EOS)
    rchan.success_!(r)
  }

  val raise: Signal => Unit = { s =>
    rootCtx.shutdown(s)
    rchan.failure_!(s)
  }

  val fatal: Signal => Unit = {
    case Shutdown(r) =>
      terminate(r.asInstanceOf[R])
    case Handover =>
      rootCtx.shutdown(EOS)
    case s =>
      System.err.println("FATAL:" + this + ":" + s)
      raise(s)
  }

  private[this] final var flushables = HashSet.empty[Flushable]

  def setDirty(f: Flushable): Unit =
    flushables += f

  def unsetDirty(f: Flushable): Unit =
    flushables -= f

  def isDirty(f: Flushable): Boolean =
    flushables.contains(f)

  def flush(uthread: UThread): Unit = {
    if (!flushables.isEmpty) {
      val fs = flushables
      flushables = HashSet.empty[Flushable]
      val it = fs.iterator
      while (it.hasNext) {
        it.next().flush(uthread)
      }
    }
  }

  def start(thread: UThread, behavior: io.IO[R]) = {

    val root = new UThreadContext(this, thread, rootCtx, raise, fatal)

    root.submit(behavior.ask(root, terminate))

  }

}