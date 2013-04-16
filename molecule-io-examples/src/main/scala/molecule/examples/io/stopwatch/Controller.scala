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

package molecule.examples.io.stopwatch

import molecule._
import io._
import stream._
import channel.Timer
import java.util.concurrent.TimeUnit

sealed abstract class Event
case object Start extends Event
case object Split extends Event
case object Unsplit extends Event
case object Reset extends Event
case object Stop extends Event

object Controller extends ProcessType1x1[Event, DisplayTime, Unit] {

  val initTime = DisplayTime(0, 0, 0, 0)

  def displayTimeFeed: IChan[DisplayTime] =
    Timer.every(100, TimeUnit.MILLISECONDS).scan(initTime)((time, tick) => time.increment)

  def main(events: Input[Event], display: Output[DisplayTime]) = {

    /**
     * Reset the display after watch is stop
     */
    def ready(): IO[Unit] = {

      def waitForStart: IO[Unit] = events.read() >>\ {
        case Start =>
          use(displayTimeFeed) >>\ running
        case _ => waitForStart
      }

      display.write(initTime) >> waitForStart
    }

    def running(timeFeed: Input[DisplayTime]): IO[Unit] = {
      (events <%+> timeFeed).read() >>\ {
        case Left(Split) => paused(timeFeed)
        case Left(Stop) => stopped(timeFeed)
        case Left(_) => running(timeFeed)
        case Right(timeUpdate) => display.write(timeUpdate) >> running(timeFeed)
      }
    }

    def paused(timeFeed: Input[DisplayTime]): IO[Unit] = {
      (events <%+> timeFeed).read() >>\ {
        case Left(Unsplit) => running(timeFeed)
        case Left(Stop) => stopped(timeFeed)
        case _ => paused(timeFeed)
      }
    }

    def stopped(timeFeed: Input[DisplayTime]): IO[Unit] = {
      lazy val waitForReset: IO[Unit] = events.read() >>\ {
        case Reset => ready()
        case _ => waitForReset
      }

      timeFeed.poison() >> waitForReset
    }

    ready() orCatch {
      case EOS =>
        ioLog("closed") // timer is automatically closed thanks to ARM
    }
  }
}