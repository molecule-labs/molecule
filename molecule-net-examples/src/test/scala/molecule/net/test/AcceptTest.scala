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

package molecule
package net
package test

import java.nio.ByteBuffer
import molecule.io._
import java.net.InetSocketAddress

object AcceptTest {
  
  val count = 1000

  object Client extends ProcessType1x1[ByteBuffer, ByteBuffer, Unit] {

    def main(in:Input[ByteBuffer], out:Output[ByteBuffer]) = for {
      _  <- out.write(ByteBuffer.wrap(Array[Byte](0, 0, 222.toByte, 173.toByte)))
      bb <- in.read()
      _  <- IO(assert(bb.getInt() == 0xDEAD))
    } yield ()
  }
  
  object Servlet extends ProcessType1x1[ByteBuffer, ByteBuffer, Unit] {
    override def name = "Servlet"
      
    def main(in:Input[ByteBuffer], out:Output[ByteBuffer]) = for {
      bbIn <- in.read()
      val bbOut = bbIn.duplicate()
      val i = bbIn.getInt
      _    <- IO{assert(i == 0xDEAD, i)}
      _    <- out.write(bbOut)
    } yield ()    
  } 

  import molecule.platform.Platform
  import molecule.net.SocketOption.{SO_REUSEADDR, SO_LINGER}
  
  def main(args : Array[String]) : Unit = {
    val ns = NetSystem(Platform("server", debug = true))
    val addr = ns.launchTcpServer(Servlet, SO_REUSEADDR(true))
    
    val clientNet = NetSystem(Platform("client", debug = false))
    
    for (j <- 1 to 5) {
      for (i <- 1 to 50) {
        (1 to 5) map {_ =>
          clientNet.launchTcpClient(addr, Client, SO_LINGER(true, 0))
        } foreach {_.future.get()}
      }
      
      // Give some time to OS to reclaim existing sockets in TIME_WAIT state
      // Sometime an exception may be thrown though
      println("cycle " + j + " on " + 5 + " done")      
      if (j != 10) {
        println("sleeping " + 1 + " seconds")
        Thread.sleep(1000)
      }
    }
    
    clientNet.shutdown()
    clientNet.platform.shutdown()
    ns.shutdownNow()
    ns.platform.shutdownNow()
  }

}