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
package benchmarks.comparison
package molecule.executors

import java.util.concurrent.ExecutorService
import platform.Executor
import platform.executors.TrampolineExecutor
import platform.{ ThreadFactory, MoleculeThread }

/**
 * Executor used by the Flow Parallel Scheduler
 */
final class TrampolineTPExecutorLog(pool: ExecutorService, group: ThreadGroup) extends Executor {

  /**
   * One task queue per kernel threads. A kernel thread will submit a task to the
   * thread pool only if there are more than one task in its local task queue.
   */
  private final val context = new ThreadLocal[TrampolineTask]() {
    override protected def initialValue() = null
  }

  private[this] final class TrampolineTask( final var nextTask: Runnable) extends Runnable {
    def run() = {
      // When we reach here, the next task is null
      context.set(this)
      while (nextTask != null) {
        val task = nextTask
        nextTask = null
        task.run()
      }
    }
  }

  def execute(task: Runnable) {
    //println(Thread.currentThread())
    //println(Thread.currentThread().getThreadGroup() + "==" + group)
    // it is necessary to compare the marker trait because some frameworks like swing
    // copy the thread group of the thread that started it...
    try {
      val thread = Thread.currentThread()
      if ((thread.getThreadGroup() eq group) && thread.isInstanceOf[MoleculeThread]) {
        val trampoline = context.get()
        if (trampoline.nextTask != null) {
          //println(Thread.currentThread + ":FAIL") 
          TrampolineTPExecutorLog.submitCount.getAndIncrement()
          pool.submit(new TrampolineTask(trampoline.nextTask))
        } else {
          TrampolineTPExecutorLog.bounceCount.getAndIncrement()
        }
        trampoline.nextTask = task
      } else {
        TrampolineTPExecutorLog.submitCount.getAndIncrement()
        pool.submit(new TrampolineTask(task))
      }
    } catch {
      case t: java.util.concurrent.RejectedExecutionException =>
        // stdin is never gracefully shutdown and may submit a last key event
        // to this pool, which has been shutdown.
        if (Thread.currentThread.getThreadGroup().getName() != "stdin")
          throw t
    }
  }

  def shutdownNow() =
    pool.shutdownNow()

  /**
   * execute shutdown task.
   */
  def shutdown() =
    pool.shutdown()

}

object TrampolineTPExecutorLog {
  import java.util.concurrent.atomic.AtomicInteger
  val submitCount = new AtomicInteger(0)
  val bounceCount = new AtomicInteger(0)

  def reset() = {
    submitCount.set(0)
    bounceCount.set(0)
  }

  import java.util.concurrent.{ TimeUnit, LinkedBlockingQueue, ThreadPoolExecutor }

  //  def apply(tf:ThreadFactory, nbThreads:Int):TrampolineTPExecutorLog = 
  //    new TrampolineTPExecutorLog(new ThreadPoolExecutor(nbThreads, nbThreads,
  //                                      0L, TimeUnit.MILLISECONDS,
  //                                     new LinkedBlockingQueue[Runnable](),
  //                                      tf), tf.group)

  def apply(tf: ThreadFactory, nbThreads: Int): TrampolineTPExecutorLog = {
    val tp = new ThreadPoolExecutor(nbThreads, nbThreads,
      30L, TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable](),
      tf)
    tp.allowCoreThreadTimeOut(true)
    new TrampolineTPExecutorLog(tp, tf.group)
  }

}