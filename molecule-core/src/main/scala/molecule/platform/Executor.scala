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

/**
 * Standard molecule executor
 */
abstract class Executor extends java.util.concurrent.Executor {
  def shutdown(): Unit
  def shutdownNow(): Unit
}

object Executor {

  /**
   * Wrap a j.u.c.ExecutorService into a molecule.platform.Executor.
   *
   *  @param executor an ExecutorService
   *  @return an Executor
   */
  def wrap(executor: java.util.concurrent.ExecutorService): Executor = new Executor {
    def execute(r: Runnable): Unit = executor.execute(r)
    def shutdown(): Unit = executor.shutdown()
    def shutdownNow(): Unit = executor.shutdownNow()
  }

}