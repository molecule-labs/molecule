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

package molecule.examples.io

import molecule._
import molecule.stream._
import platform.Platform, channel.Timer
import io._

import java.util.concurrent.TimeUnit

/**
 * An example of interactive Tamagotchi console game.
 *
 * Note that this example cannot be run within Eclipse because its console
 * input is buffered and it is not supported by jline.
 *
 * Instead, you must run it from SBT like this:
 * 1. Launch sbt in molecule-core directory.
 * 2. On SBT prompt, move to the project molecule-io-examples.
 *   sbt (molecule-core-workspace)> project molecule-io-examples
 * 3. Then type 'run' and select this example.
 *   sbt (molecule-io-examples)> run
 */
object Tamagotchi extends ProcessType1x1[Char, String, Unit] {

  /**
   * Read a line from a character input stream
   */
  private def readLine(in: Input[Char]): IO[String] =
    in.span(c => c != '\r' && c != '\n').fold(new StringBuilder)(_ += _).map(_.result())

  def main(in: Input[Char], out: Output[String]): IO[Unit] = {

    /**
     * Returns true if the given key has been pressed within
     * the specified timeout, else false.
     */
    def pressKeyWithin(key: Char, timeout: Int): IO[Boolean] =
      if (timeout == 0) out.writeLn("") >> IO(false)
      else
        out.write(timeout + "...") >>
          use(Timer.timeout(1, TimeUnit.SECONDS)) >>\ { timer =>
            (in.dropWhile(_ != key) <+> timer).read() >>\ {
              _.fold(_ => out.writeLn("") >> IO(true), _ => pressKeyWithin(key, timeout - 1))
            }
          }

    /**
     * Tamagotchi is thirsty.
     *
     * Returns true if the tamagotchi has been watered within the specified
     * timeout, else false.
     */
    def thirsty: IO[Boolean] =
      out.writeLn("Can I have some (w)ater please? I'm drying...") >>
        pressKeyWithin('w', 5)

    /**
     * Tamagotchi is hungry
     *
     * Returns true if the tamagotchi has been fed within the specified
     * timeout, else false.
     */
    def hungry: IO[Boolean] =
      out.writeLn("Can I have some (f)ood please? I'm starving...") >>
        pressKeyWithin('f', 5)

    /**
     * Tamagotchi is sleepy
     */
    def sleepy: IO[Unit] =
      out.writeLn("Ahhh, I need some sleep...") >>
        // Sleep 5 seconds. Since the first rising edge of the periodic timer 
        // occurs immediately, we need 6 ticks to sleep 5 seconds.
        use(Timer.every(1, TimeUnit.SECONDS).take(6)) >>\ { timer =>
          timer.take(5).foreach { _ => out.write("Zzz...") } >>
            timer.take(1).read() >> out.writeLn("")
        } >>
        out.writeLn("Hey! It's me and I need all your lovin!")

    /**
     * Tamagotchi needs care because it is either thristy or hungry.
     * Sometimes it may sleeping though...
     *
     * The method returns None if the Tamagotchi is the end user fulfilled
     * its need (e.g. no problem). Otherwise, it returns some string that
     * indicates the cause of death.
     */
    def needCare: IO[Option[String]] = getRandom >>\ { rand =>
      val i = rand.nextInt(9)
      if (i < 4) // 40% chances of being thirsty
        thirsty.map(alive => if (alive) None else Some("dehydratation"))
      else if (i < 8) // 40% chances of being hungry
        hungry.map(alive => if (alive) None else Some("starvation"))
      else // 20% chances of being sleepy
        sleepy.map(_ => None)
    }

    /**
     * Repeats an action until it returns a string
     *
     * The 'managed' method ensures that any channel opened by
     * an action (e.g. time channels) is automatically closed
     * after the action terminates.
     */
    def tillDead(action: IO[Option[String]]): IO[String] =
      managed(action) >>\ {
        case None => tillDead(action)
        case cause => IO(cause.get)
      }

    for {
      _ <- out.write("Enter tamagotchi's name:")
      name <- readLine(in)
      _ <- out.writeLn("Hello, I'm " + name + ", your lovely tamagotchi!")
      _ <- out.writeLn("Enter 'f' to feed me or 'w' to water me.")
      deathCause <- tillDead(needCare)
      _ <- out.writeLn("Because of you, poor " + name + " died of " + deathCause + "!")
      _ <- out.writeLn("Game Over")
    } yield ()

  }

  // IMPORTANT NOTE: THIS PROGRAM DOESN'T RUN IN ECLIPSE!!!
  def main(args: Array[String]): Unit = {
    // We need jline to read interactively key presses on a terminal because
    // the default behavior is to buffer stdin.
    // Last note: jline seems broken
    // Run this process through a socket, see molecule-net-examples
    System.err.println("Until JLine support gets fixed, run this process through a socket (see molecule-net-examples)")
    return

    /*
	
    import jline.ConsoleReader
    val consoleReader = new ConsoleReader()

    val kbhit = channel.GetChan.blocking("jline")(consoleReader) { console =>
      // block invoked when the channel is read
      try {
        println("READING")
        val c = console.readVirtualKey.toChar
        print(c)
        Some(c, console)
      } catch {
        case t => println(t); None
      }
    } { _ => () } // Don't want to close stdin

    val platform = Platform("tamagotchi")

    // Wait for the result of the process: ()
    platform.launch(Tamagotchi(kbhit, channel.Console.stdout)).get_!

    // Need to flush stdin else it can have some strange effects 
    // with other applications launched by sbt
    println("Flushing stdin to return from JLine to sbt, press several times 'q' to quit.")
    while (true) {
      try {
        val c = System.in.read().toChar
        print(c)
        if (c == 'q') return
      } catch { case _ => () }
    }
	
	*/
  }
}