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
package benchmarks.comparison.actors

import scala.actors.Actor
import scala.actors.Actor._
import scala.actors.SchedulerAdapter
import java.util.concurrent.Executor

/**
 * Actor with custom executor
 */
abstract class BenchmarkActor(executor: Executor) extends Actor {

  override val scheduler = new SchedulerAdapter {
    def execute(task: => Unit) =
      executor.execute(new Runnable {
        def run() {
          task
        }
      })
  }

}
