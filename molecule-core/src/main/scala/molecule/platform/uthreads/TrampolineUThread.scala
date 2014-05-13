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
package uthreads

/**
 * Thread that execute all tasks in a trampoline excepted the first task it receives
 *
 * @author Sebastien Bocq
 */
object TrampolineUThread extends UThreadFactory {

  @inline private[this] final def wrap(task: => Unit): Runnable = new Runnable { def run() = task }

  def apply(platform: Platform, executor: Executor): UThread = new UThreadImpl(platform, executor)

  private[this] final class UThreadImpl(val platform: Platform, executor: Executor) extends UThread {

    final class Task(task: => Unit) {
      final var next: Task = _
      def run() = task
    }

    private[this] final val m = new utils.Mutex()

    private[this] final var last: Task = null
    @volatile private[this] var first: Task = null

    private[this] final object TrampolineTask extends Runnable {

      def run() = {
        // First is not read within a monitored region. 
        // Therefore, it must be volatile to be properly published. Else a thread 
        // that is different than the one that enqueued the first task might see an old task here!
        // Here, the task stream contains always one task
        //
        // doing this is wrong:
        // val tmp = first
        // first = null
        // bounce(tmp)
        // because tmp is kept until bounce finishes...
        // This is why we use an extra argument
        bounce(first, true)
      }

      @scala.annotation.tailrec
      private[this] final def bounce(task: Task, clearFirst: Boolean) {
        if (clearFirst) first = null
        try { task.run() } catch {
          case t: Throwable =>
            System.err.println("UTHREAD EXCEPTION:" + t)
            System.err.println(t.getStackTraceString)
            return
        }

        val next = task.next

        if (next eq null) {
          m.spinLock()
          val next = task.next
          if (next eq null) {
            last = null
            m.unlock()
          } else {
            m.unlock()
            bounce(next, false)
          }
        } else {
          first = null
          bounce(next, false)
        }

      }
    }

    private[this] final def enqueue(task: => Unit): Boolean = {
      val t = new Task(task)
      m.spinLock()
      if (last eq null) { // was empty
        last = t
        m.unlock()
        first = t
        true
      } else {
        last.next = t
        last = t
        m.unlock()
        false
      }
    }

    def submit(task: => Unit) =
      if (enqueue(task))
        executor.execute(TrampolineTask)

  }

}