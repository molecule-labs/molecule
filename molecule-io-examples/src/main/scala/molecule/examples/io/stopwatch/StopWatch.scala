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
import molecule.io._
import molecule.stream._

/**
 * This example is a port of the StopWatch example found in the beginner's
 * tutorial of SCXML website.
 * http://commons.apache.org/scxml/usecases/scxml-stopwatch.html
 *
 * Contrarily to the original SCXML example, we don't need XML, we get
 * the threading right (the official SCXML example suffers from data races)
 * and it scales out of the box to numerous stop watches.
 *
 */
object StopWatch extends ProcessType0x0[Unit] {

  def main() = {
    val display = new SwingDisplay(100)

    launch(Controller(display.eventCh.debug("event"), display.timeCh)).get() >>
      ioLog("closed")
  }

  import platform.Platform

  def main(args: Array[String]): Unit = {

    val platform = Platform("stopwatch", debug = true)
    platform.launch(StopWatch())
    //	Thread.sleep(1000) // Time for class loading and all
    for (i <- 1 to 2) {
      platform.launch(StopWatch())
    }

    /**
     * This will block until all processes created by this application
     * have terminated.
     */
    platform.shutdown()
    println("nothing left")
  }
}