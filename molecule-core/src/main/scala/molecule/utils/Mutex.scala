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

package molecule.utils

import java.util.concurrent.locks.{ AbstractQueuedSynchronizer, Condition }

/**
 * A simple non-reentrant lock used for exclusion when managing
 * queues and workers. We use a custom lock so that we can readily
 * probe lock state in constructions that check among alternative
 * actions. The lock is normally only very briefly held, and
 * sometimes treated as a spinlock, but other usages block to
 * reduce overall contention in those cases where locked code
 * bodies perform allocation/resizing.
 *
 * Code and comments borrowed from Doug Lea's FJPool.
 */
final class Mutex extends AbstractQueuedSynchronizer {
  final override def tryAcquire(ignore: Int): Boolean = {
    compareAndSetState(0, 1)
  }

  final override def tryRelease(ignore: Int): Boolean = {
    setState(0);
    true;
  }
  final def lock() { acquire(0) }
  final def unlock() { release(0) }
  final override def isHeldExclusively(): Boolean = { getState() == 1 }
  final def newCondition(): Condition = { new ConditionObject(); }
  final def spinLock(): Unit = {
    while (!compareAndSetState(0, 1)) {}
  }
}