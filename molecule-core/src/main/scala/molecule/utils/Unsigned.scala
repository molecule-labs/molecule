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
package utils

object Unsigned {
  //
  // UNSIGNED TYPES
  //

  /**
   * JVM is very good at signed ints but sucks for all unsigned stuff
   */
  type UByte = Short
  type UShort = Int
  type UInt = Long

  @inline
  def unsigned(b: Byte): UByte = (b & 0xFF).toShort

  @inline
  def signed(b: UByte): Byte = b.toByte

  @inline
  def unsigned(s: Short): UShort = s & 0xFFFF

  @inline
  def signed(s: UShort): Short = s.toShort

  @inline
  def unsigned(s: Int): UInt = s & 0xFFFFFFFFL

  @inline
  def signed(s: UInt): Int = s.toShort

  @inline
  def test_&(flags: Int, mask: Int): Boolean = (flags & mask) == mask

  @inline
  def test_&(flags: Long, mask: Long): Boolean = (flags & mask) == mask

  def setBitOn(in: Int, pos: Int): Int = in | 1 << pos
  def setBitOff(in: Int, pos: Int): Int = in & (-1 ^ (1 << pos))
  def isBitOn(in: Int, pos: Int): Boolean = (in & (1 << pos)) != 0
  def isBitOff(in: Int, pos: Int): Boolean = (in & (1 << pos)) == 0

  def setBitOn(in: Long, pos: Int): Long = in | 1L << pos
  def setBitOff(in: Long, pos: Int): Long = in & (-1L ^ (1L << pos))
  def isBitOn(in: Long, pos: Int): Boolean = (in & (1L << pos)) != 0
  def isBitOff(in: Long, pos: Int): Boolean = (in & (1L << pos)) == 0

}