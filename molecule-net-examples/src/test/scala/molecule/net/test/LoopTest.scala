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
object LoopTest {
	
  val INTS_PER_BUFFER = 3000// 3000 // 3000 Ints, 8Kb buffer => 1.5 packet per buffer
  val NB_BUFFERS = 400 //400

  def debug(op:String, buf:ByteBuffer):String = {
      val b = buf.duplicate()
      //if (op == "read") b.flip()
      val len = b.limit - b.position
      val from = b.getInt()
      b.position(b.limit - 4)
      val to = b.getInt()
      op + "[" + len + "B]:" + from + "->" + to
  }

  /** The server will assign a new instance of this input logger 
   * to each client socket
   */
  object EchoHandler extends ProcessType1x1[ByteBuffer, ByteBuffer, Unit] {
	def main(in:Input[ByteBuffer], out:Output[ByteBuffer]) = 
	  (in flush out/**.debug("o", debug("write", _))**/) >> IO() // This will run until socket is closed
  }
  
  def mkIntArray(from:Int, end:Int):Array[Int] = 
	  (from until end).toArray[Int]
  
  def toSendBuffer(ints:Array[Int]):ByteBuffer = {
	val bb = ByteBuffer.allocate(ints.length * 4)     
	bb.asIntBuffer.put(ints)
	bb
  }

  /** Seems that TCP stack preserves integer boundaries, but in case 
   * they are not this is what it takes. 
   */
  def fromRcvBuffer(prev:ByteBuffer, next:ByteBuffer):(Array[Int], ByteBuffer) = {
	if (prev.remaining + next.remaining >= 4) {
	  val rem = (prev.remaining + next.remaining) % 4
	  val ints = new Array[Int]((prev.remaining + next.remaining)/4)
	  // Merge the buffers
	  val bb = ByteBuffer.allocate(ints.size * 4)
	  val _next = next.duplicate()
	  _next.limit(next.limit - rem)
	  bb.put(prev)
	  bb.put(_next)
	  bb.flip()
	  bb.asIntBuffer.get(ints)
	  next.position(next.limit - rem)
	  (ints, next)
	} else {
	  val arr = new Array[Byte](prev.remaining + next.remaining)
      prev.get(arr, 0, prev.remaining)
      next.get(arr, prev.remaining, next.remaining)
      (Array[Int](0), ByteBuffer.wrap(arr))
	}
  }
  
  def intsToString(ints:Array[Int]):String = 
	ints.mkString("[", ",", "]")
  
  /** Clients will stream some bytes to the server
   */
  object ClientHandler extends ProcessType1x1[ByteBuffer, ByteBuffer, Unit] { 
	 
	def main(_in:Input[ByteBuffer], out:Output[ByteBuffer]) = { 
	  val in    = _in.debug("i", debug("read", _))
	  for {
     src       <- use(IChan.source(Range(0, NB_BUFFERS), 1 /**yield every 1*/))
     _         <-
         (
           src.foreach {i =>
             val from = i * INTS_PER_BUFFER
             val to   = i * INTS_PER_BUFFER + INTS_PER_BUFFER
             val ints = mkIntArray(from, to)
             val sndbuf = toSendBuffer(ints)
             ioLogDbg("sending["+ints.size * 4+"B]: " + from + "->" + to) >> out.write(sndbuf) 
	       } >> 
	       ioLog("Done sending")
	     ) |~|
	     {
	       def loop(intCount:Int, acc:ByteBuffer, ret:Int => IO[Nothing]):IO[Nothing] =
        	 if (intCount == NB_BUFFERS * INTS_PER_BUFFER) {
        	   ret(intCount)
        	 } else {
	           in.read() >>\ {rcvbuf => 
	             val (ints, r) = fromRcvBuffer(acc, rcvbuf)
	             ioLogDbg("received["+ints.size * 4+"B, rem:"+r.remaining+"B]:" + ints.head + "->" + ints.last) >> 
                 loop(intCount + ints.size, r, ret)
               }
        	 }
	       callcc[Int](loop(0, ByteBuffer.allocate(0), _)) >>\ {count => assert(count == INTS_PER_BUFFER * NB_BUFFERS); ioLog("SUCCESS! :" + count.toString)}
		 }
    } yield()
  }
  }
  
  def main(args : Array[String]) : Unit = {
	  
	val serverNet = NetSystem(Platform("server", debug = false))
    val addr = serverNet.launchTcpServer(EchoHandler)
  
    val time = System.nanoTime()
    
    val clientNet = NetSystem(Platform("client", debug = false))
    for (i <- 1 to 2) { 
      (1 to 4) map {_ => 
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
