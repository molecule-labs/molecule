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
package signals

/**
 * Signal that indicates an exception was thrown.
 */
class JThrowable private (val t: Throwable) extends ErrorSignal {
  override def toString() = {
    "JThrowable(" +
      t.toString() + ")\r\n" +
      stackTrace(20) +
      ")"
  }

  private[this] def stackTrace(n: Int): String = {
    import scala.collection.JavaConversions._

    val rem = t.getStackTrace.length - n

    t.getStackTrace.take(n).mkString("\r\n") +
      (if (rem > 0)
        "\r\n" + rem + " more..."
      else ""
      )
  }
}

object JThrowable {

  def apply(t: Throwable): Signal = t match {
    case s: SignalException => s.signal
    case t => new JThrowable(t)
  }

  def unapply(jt: JThrowable): Option[Throwable] = Some(jt.t)

}