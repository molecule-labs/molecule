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

trait MoleculeThread extends Thread {
  def getRunnable(): Runnable // FJ compat
}

/**
 * Thread factory.
 */
final class ThreadFactory(name: String, val daemon: Boolean = false) extends java.util.concurrent.ThreadFactory {

  private[this] final var s = java.lang.System.getSecurityManager();

  val group: ThreadGroup = new ThreadGroup(name)

  //    if (s != null)
  //	  						s.getThreadGroup()
  //                          else
  //                            Thread.currentThread().getThreadGroup();

  import java.util.{ concurrent => juc }
  val threadNumber = new juc.atomic.AtomicInteger(1);

  val namePrefix = "molecule-" + name + "/thread-"

  def newThread(r: Runnable): Thread = newThread("", r)

  def newThread(name: String, r: Runnable): Thread = {
    val t = new Thread(group,
      r,
      namePrefix + threadNumber.getAndIncrement(),
      0) with MoleculeThread {
      def getRunnable() = r // for fork join pool
    }

    // Not sure, but setDaemon may have some cost
    if (t.isDaemon() && !daemon)
      t.setDaemon(false)
    else if (!t.isDaemon() && daemon)
      t.setDaemon(true)

    if (t.getPriority() != Thread.NORM_PRIORITY)
      t.setPriority(Thread.NORM_PRIORITY);

    t;
  }
}
