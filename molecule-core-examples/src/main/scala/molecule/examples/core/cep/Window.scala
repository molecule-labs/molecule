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

package molecule.examples.core.cep

import molecule._
import molecule.stream._

trait Window[A] {
  def add(a: A): Window[A]
  def window: Seg[A]
}

case class SizeWindow[A](size: Int, window: Seg[A]) extends Window[A] {
  def add(a: A): SizeWindow[A] = {
    if (window.size < size) copy(window = window :+ a)
    else copy(window = window.tail :+ a)
  }
}

case class TimeWindow[A](time: Long, window: Seg[(Long, A)]) extends Window[(Long, A)] {
  def add(a: (Long, A)): TimeWindow[A] = {
    val ts = a._1
    val nwindow = window.dropWhile { case (t, v) => t - ts >= time }
    copy(window = window :+ a)
  }
}
