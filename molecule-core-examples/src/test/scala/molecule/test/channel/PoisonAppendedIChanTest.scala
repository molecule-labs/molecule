/*
 * Copyright (C) 2015 Alcatel-Lucent.
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

package molecule.test.channel

import molecule._
import molecule.stream._
import molecule.platform.Platform
import molecule.channel.Console

object PoisonAppendedIChanTest {

  def main(args: Array[String]): Unit = {

    val a = RepeatChannel("a")
    val b =  RepeatChannel("b")
    val c = RepeatChannel("c")
    
    val ichan = a append b append c
    
    ichan.poison(EOS)
    // this MUST poison all appended channels
    
    Platform("").launch(ichan connect Console.stdoutLine).get_!

  }

}