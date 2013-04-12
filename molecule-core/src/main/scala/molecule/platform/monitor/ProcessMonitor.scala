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
package platform
package monitor

import process.Process
import channel.{ ROChan, RIChan }

class ProcessMonitor(name: String, val debug: Boolean = false) { outer =>

  private[this] final var count: Int = 0

  final def apply[R: Message](process: Process[R]): Process[R] = {

    val mprocess: Process[R] = new Process[R] {

      def ptype = process.ptype

      def start(t: UThread, rchan: ROChan[R]): Unit = {
        val mrchan: ROChan[R] = new ROChan[R] {
          def done(r: Either[Signal, R]) = {
            pstop(process.toString, r)
            rchan.done(r)
          }
        }
        pstart(process.toString)
        process.start(t, mrchan)
      }
    }

    mprocess
  }

  def pstart(process: String) = synchronized {
    count += 1
    if (debug) println("start:" + name + "/" + process + ":" + count)
  }

  def pstop(process: String, result: Either[Signal, Any]) = synchronized {
    count -= 1
    result match {
      case Right(res) =>
        if (debug) println("stop:" + name + "/" + process + ":" + count + " -> " + res)
      case Left(s) =>
        System.err.print("stop:" + name + "/" + process + ":" + count + " -> SIGNAL:")
        System.err.println(s)
    }

    if (count == 0)
      this.notify()
  }

  def isZero = synchronized {
    count == 0
  }
  def await() = synchronized {
    if (count != 0)
      this.wait()
  }

  override def toString = count.toString
}

object ProcessMonitor {
}