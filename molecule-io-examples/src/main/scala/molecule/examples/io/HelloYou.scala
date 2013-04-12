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
 * Hello World
 */
object HelloYou extends ProcessType1x1[String, String, String] {

  def main(in: Input[String], out: Output[String]) = for {
    _ <- out.write("What is your name?")
    name <- in.read()
    _ <- out.write("Hello " + name + "!")
  } yield name

  import molecule.platform.Platform
  import molecule.channel.Console

  def main(args: Array[String]): Unit = {
    // Create an execution platform
    val platform = Platform("hello-you")

    // Launch an instance of the HelloYou component on the platform
    // and get its result.
    val name = platform.launch(HelloYou(Console.stdinLine, Console.stdoutLine)).get_!()
    println("Said hello to " + name)

  }

}
