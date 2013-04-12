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
package net

import java.nio.ByteBuffer
import channel.{ OChan, IChan, Chan }
import java.nio.channels.{ SelectableChannel => JSelectableChannel, ReadableByteChannel }

/**
 * Base trait for Socket input channels.
 *
 */
trait InputChannel[T] extends Channel {

  protected val socket: Socket[T]
  protected val selector: IOSelector
  protected val rcvBuf: ByteBuffer

  private final var ochan: OChan[T] = _

  private final var updateTask: Runnable = null

  private final val update: OChan[T] => Unit = { ochan =>
    submit(new Runnable { def run = InputChannel.this.ochan = ochan })
  }

  def submit(updateTask: Runnable): Unit = {
    if (Thread.currentThread.getId != selector.threadId)
      selector.schedule(new Runnable {
        def run = {
          updateTask.run()
          ochan match {
            case OChan(_) =>
              closed()
            case _ =>
              selector.registerRead(socket)
          }
        }
      })
    else
      this.updateTask = updateTask
  }

  final def readReady(): Unit = {
    val size =
      try {
        doRead(rcvBuf)
      } catch {
        case e: java.io.IOException =>
          ochan.close(SocketReadSignal(e))
          selector.clearRead(socket)
          closed()
          return
        case t =>
          ochan.close(Signal(t))
          selector.clearRead(socket)
          closed()
          return
      }

    if (size == 0) {
      return
    } else if (size < 0) {
      ochan.close(EOS)
      selector.clearRead(socket)
      closed()
      return
    } else {
      rcvBuf.flip()
      val seg = doCopy(rcvBuf)
      rcvBuf.clear()

      ochan.write(seg, None, update)

      if (updateTask == null) {
        selector.clearRead(socket)
      } else {
        val tmp = updateTask
        updateTask = null
        tmp.run()

        ochan match {
          case OChan(_) =>
            selector.clearRead(socket)
            closed()
          case _ =>
        }
      }
    }
  }

  private class PoisonWrapper[T](ichan: IChan[T]) extends IChan[T] {

    def read(k: (Seg[T], IChan[T]) => Unit) {
      ichan.read((seg, ichan) => ichan match {
        case IChan(signal) =>
          k(seg, ichan)
        case _ =>
          k(seg, new PoisonWrapper(ichan))
      })
    }

    def poison(signal: Signal) {
      ichan.poison(signal)
      selector.schedule(new Runnable { def run = closed() })
    }
  }

  def init(): IChan[T] = new IChan[T] {

    def read(k: (Seg[T], IChan[T]) => Unit) {
      val (i, o) = channel.Chan.mk[T]()(PureMessage)

      selector.schedule(new Runnable {
        def run = {
          ochan = o
          selector.registerRead(socket)
        }
      })
      new PoisonWrapper(i).read(k)
    }

    def poison(signal: Signal) {
      selector.schedule(new Runnable { def run = closed() })
    }
  }

  def doRead(rcvBuf: ByteBuffer): Int
  def doCopy(rcvBuf: ByteBuffer): Seg[T]
  def closed(): Unit
}