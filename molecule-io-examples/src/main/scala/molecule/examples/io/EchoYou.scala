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
import molecule.io._

/**
 * Terminal
 */
object EchoYou extends ProcessType1x1[String, String, Unit] {

  def welcome(name: String) =
    """Hello """ + name + """!
Please enter some lines and I'll echo them to you.
You can enter special commands starting with ":" to change 
the echo behavior. They are:
 :reverse -> to reverse strings echoed or revert the behavior
 :quit    -> to quit the session
"""

  val UnknownCommand = """(:.*)""".r

  def main(in: Input[String], out: Output[String]) = {

    def process(reversed: Boolean, f: String => String): IO[Unit] =
      in.read() >>\ {
        case ":reverse" =>
          if (reversed)
            process(false, identity)
          else
            process(true, _.reverse)
        case ":quit" =>
          IO()
        case UnknownCommand(cmd) =>
          out.write("Command '" + cmd + "' unknown") >> process(reversed, f)
        case s =>
          out.write(f(s)) >> process(reversed, f)
      }

    for {
      _ <- out.write("What is your name?")
      name <- in.read()
      _ <- out.write(welcome(name))
      _ <- process(false, identity)
    } yield ()
  }

  import platform.Platform
  import channel.Console

  def main(args: Array[String]): Unit = {
    val platform = Platform("echo-you")

    platform.launch(EchoYou(Console.stdinLine, Console.stdoutLine)).get_!()

  }

}