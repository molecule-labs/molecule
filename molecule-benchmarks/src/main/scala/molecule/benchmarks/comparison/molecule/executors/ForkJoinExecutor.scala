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
import jsr166y._

/**
 * Used by ActorLikeScheduler
 */
object ForkJoinExecutor {

  class FJExecutor(nbThreads: Int, factory: ForkJoinPool.ForkJoinWorkerThreadFactory) extends Executor {
    private[this] final val s = new ForkJoinPool(nbThreads, factory, null, true)

    def execute(r: Runnable) =
      try {
        s.execute(r)
      } catch {
        case e: java.util.concurrent.RejectedExecutionException => ()
      }

    override def shutdown() =
      s.shutdown()

    /**
     * Shutdown immediately
     */
    def shutdownNow() =
      s.shutdown()
  }

  def apply(tf: ThreadFactory, nbThreads: Int): Executor = {
    val f = new ForkJoinPool.ForkJoinWorkerThreadFactory {
      def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
        return new ForkJoinWorkerThread(pool, tf);
      }
    }
    new FJExecutor(nbThreads, f)
  }
}