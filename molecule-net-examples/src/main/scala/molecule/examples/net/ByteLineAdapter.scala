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

import molecule.process.ProcessType1x1
import molecule.parsers.charbuffer.line
import molecule.net._
import molecule.utils.{ decode, encode }

import java.nio.{ ByteBuffer, CharBuffer }

object ByteLineAdapter {

  def adapt[R](ptype: ProcessType1x1[String, String, R], charset: String): ProcessType1x1[ByteBuffer, ByteBuffer, R] =
    ptype.adapt[ByteBuffer, ByteBuffer](
      _.map(decode(charset)).parse(line(2048)),
      _.map(encode(charset)).map { s: String => CharBuffer.wrap(s + "\r\n") }
    )

}