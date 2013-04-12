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

object JStreamAdapter {
  import molecule._
  import parsing._
  import process.ProcessType1x1

  import parsers.bytebuffer._
  import java.io.{ ObjectInputStream, ByteArrayInputStream, ObjectOutputStream, ByteArrayOutputStream }
  import java.nio.ByteBuffer

  def encode[A](a: A): Seg[ByteBuffer] = {
    val byteStream = new ByteArrayOutputStream()
    new ObjectOutputStream(byteStream).writeObject(a)
    val payload = ByteBuffer.wrap(byteStream.toByteArray())
    val header = ByteBuffer.allocate(4)
    header.putInt(payload.remaining)
    header.flip()
    Seg(header, payload)
  }

  def decode[A]: Parser[ByteBuffer, A] = for {
    len <- anyInt
    payload <- byteArray(len)
  } yield {
    new ObjectInputStream(new ByteArrayInputStream(payload)).readObject.asInstanceOf[A]
  }

  def apply[A: Message, B: Message, R: Message](process: ProcessType1x1[A, B, R]): ProcessType1x1[ByteBuffer, ByteBuffer, R] =
    process.adapt[ByteBuffer, ByteBuffer](_.parse(decode[A]), _.flatMap(encode[B]))

}

//case class JStreamAdapter[IN: Message, OUT: Message, R: Message](
//  ptype: ProcessType1x1[IN, OUT, R]) extends ProcessType1x1[ByteBuffer, ByteBuffer, R] {
//  
//  override def name = ptype.name
//  
//  import JStreamAdapter.{decode, encode}
//  
//  def main(in: Input[ByteBuffer], out: Output[ByteBuffer]) =
//    handover {
//      ptype(
//        in.parse(decode[IN]),
//        out.flatMap(encode[OUT]))
//    }
//}
//
//object JStreamAdapter {
//  
//  import parsing._ 
//  import parsers.bytebuffer._
//  import seg._
//  import java.io.{ ObjectInputStream, ByteArrayInputStream }
//
//  def encode[A](a:A):Seg[ByteBuffer] = {
//    val byteStream = new ByteArrayOutputStream()
//    new ObjectOutputStream(byteStream).writeObject(a)
//    val payload = ByteBuffer.wrap(byteStream.toByteArray())
//    val header = ByteBuffer.allocate(4)
//    header.putInt(payload.remaining)
//    header.flip()
//    Seg(header, payload)
//  }
//  
//  def decode[A]:Parser[ByteBuffer, A] = for {
//    len     <- byteBuffer(4) ^^ { _.getInt() }
//    payload <- byteArray(len)
//  } yield {
//    new ObjectInputStream(new ByteArrayInputStream(payload)).readObject.asInstanceOf[A]
//  }
//
//}

