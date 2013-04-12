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
import channel.{ Chan, IChan, OChan }
import java.nio.channels.{ SelectableChannel => JSelectableChannel }

/**
 * Base trait for Socket output channels.
 *
 */
trait OutputChannel[T] extends Channel {

  protected val socket: Socket[T]
  protected val selector: IOSelector
  protected val sndBuf: ByteBuffer

  private var ichan: IChan[T] = _
  private var seg: Seg[T] = NilSeg

  private final var updateTask: Runnable = null

  private final val update: (Seg[T], IChan[T]) => Unit = { (seg, ichan) =>
    submit(new Runnable {
      def run = {
        OutputChannel.this.seg = seg
        OutputChannel.this.ichan = ichan
      }
    })
  }

  def submit(updateTask: Runnable): Unit = {
    //System.err.println(niochan.hashCode() +":channel has more to write")
    if (Thread.currentThread.getId != selector.threadId)
      selector.schedule(new Runnable {
        def run = {
          updateTask.run()
          //System.err.println(niochan.hashCode() +":Asynchronous write:" + seg)
          if (seg.isEmpty) {
            ichan match {
              case IChan(signal) =>
                closed()
              case nichan =>
                read(nichan)
            }
          } else {
            selector.registerWrite(socket)
          }
        }
      })
    else
      this.updateTask = updateTask
  }

  private[this] final var remaining = false

  final def writeReady(): Unit = {

    if (!remaining) {
      if (seg.isEmpty) {
        ichan match {
          case IChan(signal) =>
            selector.clearWrite(socket)
            closed()
            return
          case _ =>
            val e = new Exception("Invalid state reached in nio.OutputChannel!")
            ichan = IChan.empty(Signal(e))
            selector.clearWrite(socket)
            closed()
            throw e
        }
      }
      seg = doCopy(seg, sndBuf)
      sndBuf.flip()
    }

    try {
      doWrite(sndBuf)
    } catch {
      case e: java.io.IOException =>
        println("Send failed. ")
        ichan.poison(SocketWriteSignal(e))
        selector.clearWrite(socket)
        closed()
        return
      case t =>
        selector.clearWrite(socket)
        ichan.poison(Signal(t))
        closed()
        return
    }

    if (sndBuf.remaining == 0) {
      remaining = false
      sndBuf.clear()

      if (!seg.isEmpty)
        return

    } else {
      remaining = true
      return
    }

    ichan match {
      case IChan(signal) =>
      // seg empty and IChan close => will be closed next time write is ready
      case _ =>
        //System.err.println(niochan.hashCode() +":ready to write again")
        read(ichan)
    }
  }

  private final def read(_ichan: IChan[T]): Unit = {
    _ichan.read(update)

    if (updateTask != null) {
      val tmp = updateTask
      updateTask = null
      tmp.run()

      //System.err.println(niochan.hashCode() +":synchronous write:" + seg)
      if (seg.isEmpty) {
        ichan match {
          case IChan(signal) =>
            closed()
          case nichan =>
            read(nichan)
        }
      }
    } else {
      selector.clearWrite(socket)
    }

  }

  def init(): OChan[T] = new OChan[T] { ochan =>
    val t = new java.util.concurrent.atomic.AtomicInteger(0)

    def write(seg: Seg[T], sigOpt: Option[Signal], k: OChan[T] => Unit) {
      if (t.getAndIncrement() != 0) {
        val e = new Exception("Invalid state reached in nio.OutputChannel! Somebody broke the rule of not re-using a ochan seed.")
        k(OChan[T](Signal(e))(PureMessage))
        throw e
      }

      selector.schedule(new Runnable {
        def run: Unit = {

          if (seg.isEmpty) {
            if (sigOpt.isEmpty) {
              k(ochan)
            } else {
              close(sigOpt.get)
            }
            return ()
          }

          OutputChannel.this.seg = seg

          if (sigOpt.isDefined) {
            ichan = IChan.empty(sigOpt.get)
          } else {
            val (i, o) = channel.Chan.mk[T]()(PureMessage)
            ichan = i
            k(o)
          }

          selector.registerWrite(socket)
        }
      })
    }

    def close(signal: Signal) {
      selector.schedule(new Runnable { def run = closed() })
    }
  }

  protected def doCopy(seg: Seg[T], sndBuf: ByteBuffer): Seg[T]

  protected def doWrite(sndBuf: ByteBuffer): Unit

  protected def closed(): Unit

}