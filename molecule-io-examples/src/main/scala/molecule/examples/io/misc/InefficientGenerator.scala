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

package molecule.examples.io.misc

import molecule._
import molecule.io._

/**
 * Shows two ways to generate input streams.
 *
 * The bad is to generate data out of the blue inside a process. This
 * is very inefficient because messages are generated one at a time.
 *
 * The good way is to use one of the many constructors and combinators
 * in IChan. @see molecule.channel.sys.IChan
 *
 */
object InefficientGenerator extends ProcessType0x1[Int, Unit] {

  def repeat[A](n: Int)(f: Int => IO[A]): IO[Unit] = {

    def loop(i: Int): IO[Unit] = {
      if (i >= n) IO()
      else f(i) >> loop(i + 1)
    }
    loop(0)
  }

  import platform.Platform
  import channel.Console

  def main(out: Output[Int]) =
    repeat(10000) { out.write }

  def main(args: Array[String]): Unit = {

    implicit val platform = Platform("inefficient-generator-example")
    platform.launch(InefficientGenerator(Console.logOut[Int]("log"))).get_!()

    // This is more concise and faster:
    // (IChan(0 to 9999) connect OChan.logOut[Int]("log2")).get_!()

  }
}