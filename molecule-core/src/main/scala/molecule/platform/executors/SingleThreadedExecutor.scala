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
 * Class of executors bound to a single thread.
 *
 * Can also be use for scheduling components that use
 * libraries that require ThreadLocal state (e.g. OpenGL).
 * See:
 * "Extending the Haskell Foreign Function Interface with
 * Concurrency" -
 * Simon Marlow and Simon Peyton, Jones Wolfgang Thaller
 */
trait SingleThreadedExecutor extends Executor

object SingleThreadedExecutor {

  def apply(tf: ThreadFactory): SingleThreadedExecutor =
    new CLBQExecutor(tf)

  def apply(threadName: String, daemon: Boolean = true): SingleThreadedExecutor =
    new CLBQExecutor(new ThreadFactory(threadName, daemon))

  private[this] final class CLBQExecutor(
      tf: ThreadFactory) extends SingleThreadedExecutor {

    lazy val queue = new utils.ConcurrentLinkedBlockingQueueSC[Runnable](t)

    private[this] val worker = new Runnable() {

      @volatile
      var thread: Thread = null

      final def run() {
        thread = Thread.currentThread

        while (thread != null) {

          try {
            val reaction = queue.take
            reaction.run()
          } catch {
            case t: java.lang.InterruptedException =>
              if (thread != null) {
                System.err.println("In SingleThreadedExecutor, thread " + thread + " :")
                // Seems stdin is tricky...
                System.err.println(t)
                System.err.println("Ooops, you must be running through SBT...")
                System.err.println("... and stdin thread is not terminated gracefully.")
                System.err.println("You may have to restart the interpreter if you encounter stability issues")
                //System.err.println(t.getStackTraceString)
                thread = null
              }
              Thread.interrupted() // clear status
            // return
            case t: java.util.concurrent.RejectedExecutionException =>
              if (thread != null) {
                System.err.println(t)
                System.err.println(t.getStackTraceString)
                thread = null
              }
              return
            case t: Throwable =>
              System.err.println(t)
              System.err.println(t.getStackTraceString)
              thread = null
              return
          }
        }
      }
    }

    private[this] val t: Thread = tf.newThread(worker)
    t.start

    def execute(runnable: Runnable): Unit = {
      queue.offer(runnable)
    }

    import java.lang.management.ManagementFactory

    def shutdown() = shutdownNow()

    def shutdownNow() = {
      val moribund = synchronized {
        val tmp = worker.thread
        worker.thread = null;
        tmp
      };
      if (moribund != null) {
        //val mx = ManagementFactory.getThreadMXBean();
        //val time = mx.getThreadCpuTime(moribund.getId)
        //println("CPU time spent by executor " + this + "=" + time + "ns")
        if (!tf.daemon) moribund.interrupt();
      }
      val l = new java.util.LinkedList[Runnable]
      queue.drainTo(l)
      l
    }

    override def toString = t.toString
  }

}
