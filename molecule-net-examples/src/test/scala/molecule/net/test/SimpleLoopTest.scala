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

/** This is more a stability test than a really a good example 
 */
object SimpleLoopTest {
	
  val COUNT = 1000
  
  /** The server will assign a new instance of this input logger 
   * to each client socket
   */
  object EchoHandler extends ProcessType1x1[ByteBuffer, ByteBuffer, Unit] {
	def main(in:Input[ByteBuffer], out:Output[ByteBuffer]) = 
	  (in forward out) >> IO() // This will run until socket is closed
  }
      
  /** Clients will stream some bytes to the server
   */
  object ClientHandler extends ProcessType1x1[ByteBuffer, ByteBuffer, Unit] { 
	 
	def main(in:Input[ByteBuffer], out:Output[ByteBuffer]) = for {
     src       <- open(IChan.source(Range(0, COUNT), 1 /**yield every 1*/))
     _         <-
         (
           src.foreach {i =>
             val sndbuf = ByteBuffer.allocate(4)
             sndbuf.putInt(i)
             sndbuf.flip()
             ioLogDbg("sending:" + i) >> out.write(sndbuf) 
	       } >> 
	       ioLog("Done sending")
	     ) |~|
	     {
	       def loop(count:Int, ret:Int => IO[Nothing]):IO[Nothing] =
        	 if (count == COUNT) {
        	   ioLog("ret") >> ret(count)
        	 } else
	           in.read() >>\ {rcvbuf =>
	             def assertLoop(buf:ByteBuffer, count:Int):Int = {
	               if (buf.limit - buf.position > 0) {
	                 val i = rcvbuf.getInt()
	                 assert(i == count, i + "!=" + count)
	                 assertLoop(buf, count + 1)
	               } else
	                 count
	             }
	             val ncount = assertLoop(rcvbuf, count)
	             ioLogDbg("received from: " + count + " till:"+ (ncount - 1)) >> 
                 loop(ncount, ret)
               }
	       
	       callcc[Int](loop(0, _)) >>\ {count => assert(count == COUNT); ioLog("SUCCESS! :" + count.toString)}
		 }
    } yield()
  }
    
  def main(args : Array[String]) : Unit = {
	  
	val serverNet = NetSystem(Platform("server", debug = true))
    val addr = serverNet.launchTcpServer(EchoHandler)
  
    val time = System.nanoTime()
    
    val clientNet = NetSystem(Platform("client", debug = true))
    for (i <- 1 to 2) { //2
      (1 to 4) map {_ => // 4
        clientNet.launchTcpClient(addr, ClientHandler)
      } foreach {_.get_!()}
    }
	
	println((System.nanoTime - time) / 1000000)
	
	clientNet.shutdown()
	serverNet.shutdownNow()
	clientNet.platform.shutdown()
	serverNet.platform.shutdownNow()
  }
}
