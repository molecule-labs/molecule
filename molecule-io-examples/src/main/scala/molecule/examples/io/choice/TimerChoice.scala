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

package molecule.examples.io.choice

import molecule._
import molecule.io._

import java.util.concurrent.TimeUnit

/**
 * Illustrates the implementation of a readWithin methods that read a value
 * on one stream unless a timeout occurred.
 *
 * The application terminates when the input stream is closed. This uses a
 * variation of the choice operator noted <%+>, which closes the resulting
 * stream if its left input is closed. In this case the read() call in the
 * readWithin method will thow a user-level exception since no more data is
 * available. This user-level exception handler defined outside the readWithin
 * method will then simply shutdown the process.
 *
 */
object TimerChoice extends ProcessType1x1[Int, Int, Unit] { outer =>

  def main(i1: Input[Int], o1: Output[Int]) = {

    def loop: IO[Nothing] =
      readWithin(i1, 2, TimeUnit.SECONDS) >>\ {
        opt => o1.write(opt.getOrElse(-1))
      } >> loop

    loop.orCatch {
      case _ => ioLog("Main input closed!")
    }
  }

  import molecule.platform.Platform
  import molecule.channel.{ Console, NativeProducer }

  def main(args: Array[String]): Unit = {

    val (i, o) = NativeProducer.mkOneToOne[Int](10)

    val log = Console.logOut[Int]("log")

    val platform = Platform("timer-choice")

    val ri = platform.launch(TimerChoice(i, log))

    Thread.sleep(1000)
    o.send(1)
    Thread.sleep(3000)
    // Missed deadline => should print (-1)
    o.send(1)
    Thread.sleep(500)
    o.send(1)
    o.close(EOS)

    ri.get_!()
  }
}
