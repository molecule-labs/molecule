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
package molecule.schedulers

import platform.{ Scheduler, SchedulerFactory }

object FlowParallelScheduler {

  import platform.uthreads.TrampolineUThread
  import platform.executors.TrampolineExecutor

  import molecule.executors.{ TrampolineFJExecutorLog, TrampolineTPExecutorLog }
  import molecule.uthreads.TrampolineUThreadLog

  //
  // Flow parallel scheduler backed up by a thread pool
  //
  def threadPool(nbThreads: Int): SchedulerFactory =
    new SchedulerFactory({ (threadFactory, platform) =>
      new Scheduler("fptp", TrampolineUThread, TrampolineExecutor.threadPool(threadFactory, nbThreads), platform)
    })

  def threadPoolLog(nbThreads: Int): SchedulerFactory = new SchedulerFactory({ (threadFactory, platform) =>
    require(nbThreads > 0, "Cannot create a scheduler with less than one thread")
    new Scheduler("fptp-log", TrampolineUThreadLog, TrampolineTPExecutorLog(threadFactory, nbThreads), platform)
  })

  //
  // Flow parallel scheduler backed up by the fork-join pool
  //

  def forkJoin(nbThreads: Int): SchedulerFactory = new SchedulerFactory({ (threadFactory, platform) =>
    require(nbThreads > 0, "Cannot create a scheduler with less than one thread")
    new Scheduler("fpfj", TrampolineUThread, TrampolineExecutor.forkJoin(threadFactory, nbThreads), platform)
  })

  def forkJoinLog(nbThreads: Int): SchedulerFactory = new SchedulerFactory({ (threadFactory, platform) =>
    require(nbThreads > 0, "Cannot create a scheduler with less than one thread")
    new Scheduler("fpfj-log", TrampolineUThreadLog, TrampolineFJExecutorLog(threadFactory, nbThreads), platform)
  })

}

