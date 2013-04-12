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

package molecule.examples.net

import molecule._
import molecule.io._
import molecule.net._
import molecule.utils.decode
import molecule.channel.Console

import java.nio.ByteBuffer

object NetTerm extends ProcessType1x1[ByteBuffer, ByteBuffer, Unit] {

  val defaultAddress = "127.0.0.1"
  val defaultPort = 6677

  def main(netin: Input[ByteBuffer], netout: Output[ByteBuffer]) = {
    ioLog("\r\n  Press Ctrl-D (linux) or Ctrl-Z (windows) to abort session") >>
      (open(Console.stdoutByteBuffer(decode("ISO-8859-1"))) ~ open(Console.stdinByteBuffer)) >>\ {
        case (stdout ~ stdin) => connect(stdin, netin, stdout, netout)
      }
  }

  def connect(stdin: Input[ByteBuffer], netin: Input[ByteBuffer], stdout: Output[ByteBuffer], netout: Output[ByteBuffer]): IO[Unit] =
    (
      stdin.flush(netout).orCatch {
        case signal => ioLog("Network output error:" + signal) >> shutdown()
      } >>\ { signal =>
        ioLog("Standard input closed by user:" + signal) >> shutdown()
      } >> IO()
    ) |*| (
        netin.flush(stdout).orCatch {
          case signal => ioLog("Standard output closed:" + signal) >> shutdown()
        } >>\ { signal =>
          ioLog("Network input closed by peer or connection broken:" + signal) >> shutdown()
        } >> IO()
      )

}