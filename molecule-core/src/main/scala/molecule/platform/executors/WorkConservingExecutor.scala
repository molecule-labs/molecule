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
package executors

/**
 * Optimistic work-conserving executor.
 *
 * This executor is a trampoline executor that does
 * its best to find something to do before a) scheduling a task to thread pool
 * or b) returning to its thread pool where it might get suspended. Here is how it
 * proceeds:
 * a) it "pushes" the next task it cannot execute itself because it has already scheduled one
 *    to its parent, which is the executors that woke him up. If the parent has already a next
 *    task, the parent schedules the task on the thread pool.
 * b) it "steals" the next task from its parent, if it has nothing to do. The the parent has no
 *    next task the parent will forward the call to its parent, and so on. If not task was found,
 *    the executor returns back to its thread pool.
 *
 * @author Sebastien Bocq
 */
final class WorkConservingExecutor(pool: Executor, group: ThreadGroup) extends Executor {

  /**
   * One task queue per kernel threads. A kernel thread will submit a task to the
   * thread pool only if there are more than one task in its local task queue.
   */
  private final val context = new ThreadLocal[TrampolineTask]() {
    override protected def initialValue() = null
  }

  import java.util.concurrent.atomic.AtomicReference

  /**
   * Next task can be in three states:
   * - 'null' : the trampoline is running but has not yet assigned a next task.
   * - 'this' : the trampoline is not running anymore or attempting to steal the task of its parent.
   * - other task: the trampoline is running and has scheduled its next sequential task.
   *
   * if sibling, the trampoline is not running anymore.
   */
  private[this] final class TrampolineTask(
      private[this] final var nextTask: AtomicReference[Runnable],
      private[this] final var parent: TrampolineTask) extends Runnable {

    /**
     * One of the children we created has nothing more todo and attempts to
     * steal a task to from us.
     */
    private final def steal(): Runnable = {
      val current = nextTask.get()
      if ((current ne null) && (current ne this) && (nextTask.compareAndSet(current, null)))
        return current
      else
        return null
    }

    // The following performs worse than the simpler version above in the prime-sieve benchmark
    // and not much better in the other benchmarks
    //    private final def steal0():Runnable = {	  
    //      val current = nextTask.get()
    //      if ((current ne null) && (current ne this) && (nextTask.compareAndSet(current, null)))
    //        return current
    //	  else {
    //	    val p = parent
    //	    if (p == null)
    //          null
    //	    else
    //	      p.steal()
    //	  }
    //    }

    /**
     * One of the children we created attempts to push a parallel task to us
     * in a last attempt to avoid creating a new thread.
     */
    //    private final def _push(newTask:Runnable, child:TrampolineTask):Unit = {
    //	  val oldTask = nextTask.get() // either null or an old task waiting to be scheduled
    //     if ((oldTask == this) || !nextTask.compareAndSet(oldTask, newTask)) {
    //	    // this trampoline is busy, or it might have consumed the old task or submitted also a new task
    //	    val p = parent
    //		if (p == null)
    //         pool.execute(new TrampolineTask(new AtomicReference(newTask), child))
    //		else
    //		  p.push(newTask, child)
    //	  } else if (oldTask != null) // the new task chased the old task => schedule old task immediately
    //	    pool.execute(new TrampolineTask(new AtomicReference(oldTask), child))
    //    }

    /**
     * Forwarding the push call recursively could increase contention
     * but so far it does not make a difference on a 24 core machine.
     * We prefer this version to the one above because it is simpler and doesn't
     * require to declare parent as volatile.
     */
    private final def push(newTask: Runnable, child: TrampolineTask): Unit = {
      val oldTask = nextTask.get() // either null or an old task waiting to be scheduled
      if ((oldTask == this) || !nextTask.compareAndSet(oldTask, newTask)) {
        pool.execute(new TrampolineTask(new AtomicReference(newTask), child))
      } else if (oldTask != null) // the new task chased the old task => schedule old task immediately
        pool.execute(new TrampolineTask(new AtomicReference(oldTask), child))
    }

    /**
     * We created a new task
     */
    final def submit(newTask: Runnable): Unit = {
      // assert(oldTask.get() != this)
      val oldTask = nextTask.get() // either null or an old task waiting to be scheduled
      if (!nextTask.compareAndSet(oldTask, newTask)) {
        // someone pushed a task to us
        val p = parent
        if (p == null)
          pool.execute(new TrampolineTask(new AtomicReference(newTask), this))
        else
          p.push(newTask, this)
      } else if (oldTask != null) // the new task chased the old task => schedule old task immediately
        pool.execute(new TrampolineTask(new AtomicReference(oldTask), this))
    }

    def run() = {
      // When we reach here:
      // - the next task is not null because it is the initial task.
      // - the task has no child, since it cannot have created new work yet. Therefore, nextTask cannot be stealed.
      // - it may have a parent or not if the task was not created by one of the thread in this pool.
      context.set(this)
      val task = nextTask.get()
      loop(task)
    }

    @scala.annotation.tailrec
    final def loop(task: Runnable): Unit = {

      nextTask.set(null) // enable work stealing and work pushing
      task.run()
      val next = nextTask.getAndSet(this) // disable work stealing and work pushing

      if (next == null) {
        // steal-or-die
        val p = parent
        if (p != null) {
          val next = p.steal()
          if (next != null) {
            // task stolen!
            loop(next)
          } else {
            // die %(
            // Shorten the parent chain to permit garbage collection in case we are the parent of another task, 
            // which is the parent of another task, and so on.
            parent = null
          }
        }
        // dies here too %(
      } else loop(next)
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
        trampoline.submit(task)
      } else {
        pool.execute(new TrampolineTask(new AtomicReference(task), null))
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

object WorkConservingExecutor {

  import java.util.concurrent.{ TimeUnit, LinkedBlockingQueue, ThreadPoolExecutor }

  def threadPool(tf: ThreadFactory, nbThreads: Int): WorkConservingExecutor = {
    val tp = new ThreadPoolExecutor(nbThreads, nbThreads,
      30L, TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable](),
      tf)
    tp.allowCoreThreadTimeOut(true)
    new WorkConservingExecutor(Executor.wrap(tp), tf.group)
  }

  def forkJoin(tf: ThreadFactory, nbThreads: Int): WorkConservingExecutor = {
    import molecule.jsr166y._
    val f: ForkJoinPool.ForkJoinWorkerThreadFactory = new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
        return new ForkJoinWorkerThread(pool, tf);
      }
    }

    new WorkConservingExecutor(Executor.wrap(new ForkJoinPool(nbThreads, f, null, true)), tf.group)
  }

  def singleThreaded(tf: ThreadFactory): WorkConservingExecutor =
    new WorkConservingExecutor(SingleThreadedExecutor(tf), tf.group)

}