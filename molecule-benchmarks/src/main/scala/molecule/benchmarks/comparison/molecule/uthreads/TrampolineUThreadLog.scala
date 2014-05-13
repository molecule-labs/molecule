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
package benchmarks.comparison.molecule.uthreads

import platform.{ Platform, Executor, UThread, UThreadFactory }

/**
 * Thread that execute all tasks in a trampoline excepted the first task it receives
 */
object TrampolineUThreadLog extends UThreadFactory {

  @inline private[this] final def wrap(task: => Unit): Runnable = new Runnable { def run() = task }

  def apply(platform: Platform, executor: Executor): UThread = new UThread1(platform, executor)

  import java.util.concurrent.atomic.AtomicInteger
  val submitCount = new AtomicInteger(0)
  val bounceCount = new AtomicInteger(0)

  def reset() {
    submitCount.set(0)
    bounceCount.set(0)
  }

  private[this] final class UThread1(val platform: Platform, executor: Executor) extends UThread { lock =>

    import java.util.ArrayDeque
    private[this] final val taskQueue = new ArrayDeque[Runnable]

    private[this] final object TrampolineTask extends Runnable {

      def run() = {
        // Here, the task queue contains always one task
        bounce(taskQueue.getFirst())
      }

      @scala.annotation.tailrec
      private[this] final def bounce(task: Runnable) {
        try { task.run() } catch {
          case t: Throwable =>
            System.err.println("UTHREAD EXCEPTION:" + t)
            System.err.println(t.getStackTraceString)
            return
        }
        val continue = lock.synchronized {
          taskQueue.pollFirst()
          !taskQueue.isEmpty
        }
        if (continue)
          bounce(taskQueue.getFirst())
      }
    }

    def submit(task: => Unit) = {
      val resume = lock.synchronized {
        val resume = taskQueue.isEmpty
        taskQueue.add(wrap(task))
        resume
      }

      if (resume) {
        submitCount.getAndIncrement()
        executor.execute(TrampolineTask)
      } else {
        bounceCount.getAndIncrement()
      }
    }
  }

}