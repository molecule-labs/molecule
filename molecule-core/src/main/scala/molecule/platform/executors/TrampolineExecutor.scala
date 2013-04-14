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

package molecule.platform
package executors

/**
 * Executor used by the Flow Parallel Scheduler described in the paper.
 * It is kept here for reference as it has now been superseeded by
 * the WorkConservingExecutor.
 *
 * @author Sebastien Bocq
 */
final class TrampolineExecutor(pool: Executor, group: ThreadGroup) extends Executor {

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
        if (trampoline.nextTask != null)
          pool.execute(new TrampolineTask(trampoline.nextTask))
        trampoline.nextTask = task
      } else {
        pool.execute(new TrampolineTask(task))
      }
    } catch {
      case t: java.util.concurrent.RejectedExecutionException =>
        // stdin is never gracefully shutdown and may submit a last key event
        // to this pool, which has been shutdown.
        if (Thread.currentThread.getThreadGroup().getName() != "stdin")
          throw t
    }
  }

  def shutdownNow() = {
    pool.shutdownNow()
  }

  /**
   * execute shutdown task.
   */
  def shutdown() = {
    pool.shutdown()
  }

}

object TrampolineExecutor {

  import java.util.concurrent.{ TimeUnit, LinkedBlockingQueue, ThreadPoolExecutor }

  def threadPool(tf: ThreadFactory, nbThreads: Int): TrampolineExecutor = {
    val tp = new ThreadPoolExecutor(nbThreads, nbThreads,
      30L, TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable](),
      tf)
    tp.allowCoreThreadTimeOut(true)
    new TrampolineExecutor(Executor.wrap(tp), tf.group)
  }

  def forkJoin(tf: ThreadFactory, nbThreads: Int): TrampolineExecutor = {
    import molecule.jsr166y._
    val f: ForkJoinPool.ForkJoinWorkerThreadFactory = new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
        return new ForkJoinWorkerThread(pool, tf);
      }
    }

    new TrampolineExecutor(Executor.wrap(new ForkJoinPool(nbThreads, f, null, true)), tf.group)
  }

  def singleThreaded(tf: ThreadFactory): TrampolineExecutor =
    new TrampolineExecutor(SingleThreadedExecutor(tf), tf.group)

}
