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

// Following trick proposed by Jason Zaugg and Paul Philips is used to 
// disambiguate overloaded methods whose type parameters are lost
// because of type erasure.
// http://scala-programming-language.1934581.n4.nabble.com/disambiguation-of-double-definition-resulting-from-generic-type-erasure-td2327664.html
//
// To use like this.
//
//scala> object overload {
//     |    def foo[_ : __](a: List[Int]) = 0
//     |    def foo[_ : __ : __](a: List[String]) = 0
//     | }
//defined module overload
//

class __[_]
object __ {
  private[this] val ___ = new __[Any]
  implicit def make__[T] = ___.asInstanceOf[__[T]]
}
