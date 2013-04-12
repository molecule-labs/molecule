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
import molecule.net.NetSystem

object EchoYouNetTerm {

  import platform._
  def main(args: Array[String]): Unit = {

    val platform = Platform("hello-you", debug = false)
    val ns = NetSystem(platform)

    val processType = ByteLineAdapter.adapt(examples.io.EchoYou, "ISO-8859-1")
    // Through NetTerm
    val addr = ns.launchTcpServer(processType)

    val netterm = NetSystem(platform)
    netterm.launchTcpClient(addr, NetTerm).get_!()

    netterm.shutdown()
    ns.shutdownNow()
    platform.shutdownNow()
  }

}