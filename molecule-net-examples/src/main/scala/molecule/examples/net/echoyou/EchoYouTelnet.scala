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
package echoyou

import molecule._
import molecule.platform.Platform
import molecule.net._

object EchoYouTelnet extends TelnetLineAdapter(examples.io.EchoYou) {

  def main(args: Array[String]): Unit = {

    val ns = NetSystem(Platform("hello-you", debug = true))

    val addr = ns.launchTcpServer(EchoYouTelnet)

    val hname = if (addr.getHostName == "0.0.0.0") "localhost" else addr.getHostName

    println("To connect to \"Echo You\" server, open a telnet terminal to '" + hname + "':" + addr.getPort)

    println("Press enter to exit demo")
    System.in.read
    ns.shutdownNow()
  }

}