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
package io.impl

import platform.UThread
import io.Resource

/**
 * Resource context managed by user-level threads
 */
private[io] final class Context extends Resource {
  import scala.collection.immutable.HashSet

  private[this] final var children = HashSet.empty[Resource]
  private[this] final var poison: Signal = null

  def add(r: Resource): Unit =
    if (poison == null)
      children += r
    else
      r.shutdown(poison)

  def remove(r: Resource): Unit =
    children -= r

  /**
   * Shutdown the context.
   *
   * Poison any resource registered to this context with the poison
   * signal passed as argument. Every new resource added to a context
   * after it has been shutdown will be immediately poisoned.
   *
   * @param signal the poison signal
   * @return unit
   */
  def shutdown(signal: Signal): Unit = if (poison == null) {
    poison = signal
    reset(signal)
  }

  /**
   * Reset the context.
   *
   * Poison any resource registered to this context with the poison
   * signal passed as argument and remove it from resource conrol.
   *
   * @param signal the poison signal
   * @return unit
   */
  def reset(signal: Signal): Unit = {
    val resources = children
    children = HashSet.empty
    val it = resources.iterator
    while (it.hasNext) {
      it.next().shutdown(signal)
    }
  }
}