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

import java.util.{ Date, Calendar, GregorianCalendar }
import java.util.concurrent.TimeUnit

object Clock extends App {

  val initTime: Calendar = new GregorianCalendar()
  initTime.setTime(new Date(System.currentTimeMillis))

  def increment(cal: Calendar, tickCount: Int): Calendar = {
    val newCal = cal.clone.asInstanceOf[Calendar]
    newCal.add(Calendar.SECOND, 1)
    newCal
  }

  def show(cal: Calendar): String = cal.getTime.toString

  // Scan prepends initTime, which is read immediately.
  // Therefore we must introduce an initial delay before the next tick
  def timeFeed: IChan[String] =
    Timer.afterAndEvery(1, TimeUnit.SECONDS).scan(initTime)(increment).map(show).take(10)

  /**
   * The 'connect' method below takes a platform as implicit argument because it must
   * create a lightweight Connector process to send all data from an input to an output.
   */
  val p = Platform("clock")

  val stream = timeFeed connect Console.logOut[String]("Time:")

  p.launch(stream).get_!

}