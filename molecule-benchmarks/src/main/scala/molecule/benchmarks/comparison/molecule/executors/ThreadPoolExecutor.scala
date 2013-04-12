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

import platform.{ Executor, ThreadFactory }

/**
 * Executor backed up by a thread pool containing a
 * fixed number of threads
 *
 */
abstract class ThreadPoolExecutor extends Executor

object ThreadPoolExecutor {

  import java.util.concurrent.Executors
  import java.util.concurrent.ExecutorService
  import java.util.concurrent.{ ThreadPoolExecutor => JThreadPoolExecutor }
  import java.util.concurrent.TimeUnit
  import java.util.concurrent.SynchronousQueue

  private[this] final class ThreadPoolImpl(pool: ExecutorService) extends ThreadPoolExecutor {

    def execute(runnable: Runnable): Unit =
      pool.execute(runnable)

    /**
     * Submit shutdown task.
     */
    override def shutdown() =
      pool.shutdown()

    /**
     * Shutdown immediately
     */
    override def shutdownNow() =
      pool.shutdownNow()
  }

  import java.util.concurrent.{ TimeUnit, LinkedBlockingQueue }

  /**
   * Wrapper around java.util.concurrent.Executors.newFixedThreadPool
   */
  //def apply(tf:ThreadFactory, nbThreads:Int):ThreadPoolExecutor = {
  //	new ThreadPoolImpl(new JThreadPoolExecutor(nbThreads, nbThreads,
  //                                    0L, TimeUnit.MILLISECONDS,
  //                                    new LinkedBlockingQueue[Runnable](),
  //                                    tf))
  //}

  def apply(tf: ThreadFactory, nbThreads: Int): ThreadPoolExecutor = {
    val tp = new JThreadPoolExecutor(nbThreads, nbThreads,
      30L, TimeUnit.SECONDS,
      new LinkedBlockingQueue[Runnable](),
      tf)
    tp.allowCoreThreadTimeOut(true)
    new ThreadPoolImpl(tp)
  }

}
