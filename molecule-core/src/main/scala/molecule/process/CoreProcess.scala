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
package process

import platform.UThread
import channel.{ ROChan, RIChan }

abstract class CoreProcess[+R] extends Process[R] {

  def start(t: UThread, rchan: ROChan[R]): Unit =
    main(new CoreProcess.CoreUThread(t, rchan), rchan)

  def main(t: UThread, rchan: ROChan[R]): Unit

}

object CoreProcess {

  // Hot spot: We use this intermediate class to prevent Scala capturing the whole environment
  // in a thunk.
  private class Task(task: => Unit, rchan: ROChan[_]) extends Runnable {
    def run() {
      try {
        task
      } catch {
        case t: Throwable =>
          rchan.failure_!(Signal(t))
      }
    }
  }

  private class CoreUThread[R](uthread: platform.UThread, rchan: ROChan[R]) extends platform.UThread {
    def submit(task: => Unit): Unit =
      uthread.execute(new Task(task, rchan))

    def platform = uthread.platform

    override def toString = "CoreUThread:" + this.hashCode
  }

  /**
   * Factory method for a core process factory.
   * The alternative is to define a companion object that extends ProcessType
   * (see `stream.process.Connector` for example).
   *
   * @tparam E type type of environment required by each process
   * @tparam R the result of the process
   *
   * @param name the name of the process
   * @param the main method of the core process type.
   */
  def factory[E, R](name: String, main: (E, UThread) => RIChan[R]): E => CoreProcess[R] = {
    val n = name
    val m = main
    val ptyp = new ProcessType { override def name = n }
    environment => new CoreProcess[R] {
      def ptype = ptyp
      def main(t: UThread, rchan: ROChan[R]): Unit = bridge(m(environment, t), rchan)
    }
  }

  /**
   * Factory method for singleton processes
   *
   *  @param name the name of the process
   *  @param the main method of the process.
   */
  def singleton[R](name: String, main: UThread => RIChan[R]): CoreProcess[R] = {
    val n = name
    val m = main
    new CoreProcess[R] {
      val ptype = new ProcessType { override def name = n }
      def main(t: UThread, rchan: ROChan[R]): Unit = bridge(m(t), rchan)
    }
  }

  /**
   * Factory method for singleton processes based on an existing processes.
   *
   *  @param name the new name.
   *  @param process the process to rename.
   *  @return a core process
   */
  def singleton[R: Message](name: String, process: Process[R]): CoreProcess[R] = {
    val n = name
    new CoreProcess[R] {
      val ptype = new ProcessType { override def name = n }
      def main(t: UThread, rchan: ROChan[R]): Unit =
        process.start(t, rchan)
    }
  }

}