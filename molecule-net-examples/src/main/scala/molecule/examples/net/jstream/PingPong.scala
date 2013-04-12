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

package molecule.examples.net.jstream

import molecule._
import molecule.io._, molecule.net._

object PingPong {

  case class Msg(s: String, i: Int)

  object Ping extends ProcessType1x1[Msg, Msg, Unit] {
    def main(in: Input[Msg], out: Output[Msg]) =
      out.write(Msg("Ping", 0)) >>
        in.read() >>\ { m =>
          println(m)
          assert(m == Msg("Pong", 1))
          IO()
        }
  }

  object Pong extends ProcessType1x1[Msg, Msg, Unit] {
    def main(in: Input[Msg], out: Output[Msg]) =
      in.read() >>\ { m =>
        println(m)
        assert(m == Msg("Ping", 0))
        out.write(Msg("Pong", 1))
      }
  }

  import molecule.platform.Platform
  import molecule.net.SocketOption.{ SO_REUSEADDR }

  def main(args: Array[String]) {

    val net = NetSystem(Platform("pingpong"))
    val addr = net.launchTcpServer(JStreamAdapter(Pong), SO_REUSEADDR(true))

    // Launch 5 clients in parallel and wait for their termination
    (1 to 5) map { _ =>
      net.launchTcpClient(addr, JStreamAdapter(Ping))
    } foreach { _.future.get() }

    net.shutdownNow()
    net.platform.shutdownNow()
  }
}