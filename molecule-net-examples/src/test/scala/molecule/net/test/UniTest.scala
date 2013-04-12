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

import io._
import platform._
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.IntBuffer
import channel.IChan

object UniTest {

  val INTS_PER_BUFFER = 3000 // 3000 Ints, 8Kb buffer => 1.5 packet per buffer
  val NB_BUFFERS = 400

  object LogHandler extends ProcessType1x1[ByteBuffer, ByteBuffer, Unit] {
	def main(in:Input[ByteBuffer], out:Output[ByteBuffer]) = {
	  var count = 0
	  out.close() >>
	  in.foreach { bb => count += bb.remaining()/**; ioLog(bb.toString)*/;IO()} >> // This will run until socket is closed
	  IO(assert(count == INTS_PER_BUFFER * 4 * NB_BUFFERS)) >>
	  ioLog("SUCCESS!")
	}
  }
    
  object ClientHandler extends ProcessType1x1[ByteBuffer, ByteBuffer, Unit] { 
	 
	def main(in:Input[ByteBuffer], out:Output[ByteBuffer]) = {
	  in.close() >> out.connect(IChan.fill(NB_BUFFERS)(LoopTest.toSendBuffer(LoopTest.mkIntArray(0, INTS_PER_BUFFER)))).get() >> IO()
	}
  }
    
  def main(args: Array[String]): Unit = {  
    val serverNet = NetSystem(Platform("server", debug = false))
    val addr = serverNet.launchTcpServer(LogHandler)
  
    val clientNet = NetSystem(Platform("client", debug = true))
    (1 to 10) map {_ =>
      clientNet.launchTcpClient(addr, ClientHandler)
    } foreach {_.future.get()}

    // If error occurs, try to put the sleep here.
	clientNet.shutdown()
	clientNet.platform.shutdown()
	
	Thread.sleep(1000) 
    serverNet.shutdownNow()
    serverNet.platform.shutdownNow()
  }

}