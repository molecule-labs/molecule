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

package molecule.examples.core

import molecule._
import molecule.stream._
import platform.Platform, channel.{ Console, Timer }
import java.util.concurrent.TimeUnit

object Join extends App {

  def timeFeed[A: Message](as: Array[A], length: Int, period_ms: Int): IChan[A] =
    Timer.every(period_ms, TimeUnit.MILLISECONDS).map(i => as(i % as.length)).take(length)

  /**
   * The 'connect' method below takes a platform as implicit argument because it must
   * create a lightweight Connector process to send all data from an input to an output.
   */
  val p = Platform("clock")

  println("Two transitions on the left for one transition on the right")
  val stream = timeFeed(Array("a", "b"), 20, 50).join(timeFeed(Array(1, 2), 10, 100)) connect Console.logOut[(String, Int)]("log:")

  p.launch(stream).get_!

}