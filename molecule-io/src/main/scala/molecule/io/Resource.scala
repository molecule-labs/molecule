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
package io

/**
 * Type of the objects that are automatically shutdown when a process terminates.
 *
 */
trait Resource {

  /**
   * Shutdown this resource.
   *
   * @param the termination signal.
   */
  def shutdown(signal: Signal): Unit
}

object Resource {
  implicit def messageIsResource[A](a: A)(implicit ma: Message[A]): Resource =
    new Resource { def shutdown(signal: Signal): Unit = ma.poison(a, signal) }
}

private[io] class MResourceRef(
    protected var resource: Resource) extends Resource {
  def shutdown(signal: Signal): Unit =
    if (resource != null) {
      resource.shutdown(signal)
      resource = null
    }
  def reset[A <: Resource](resource: A): A = {
    this.resource = resource
    resource
  }
}

private[io] object SlaveResourceRef extends MResourceRef(null) {
  override def shutdown(signal: Signal): Unit = sys.error("Can't be called")
  override def reset[A <: Resource](resource: A): A = resource
}
