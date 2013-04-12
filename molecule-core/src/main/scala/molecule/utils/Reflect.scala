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

package molecule.utils

object Reflect {

  /**
   * Access companion object
   */
  def singleton[T](implicit man: ClassManifest[T]): T = {
    val name = man.erasure.getName()
    assert(name endsWith "$", "Not an object: " + name)
    val clazz = java.lang.Class.forName(name)

    clazz.getField("MODULE$").get(clazz).asInstanceOf[T]
  }

  def companion[T](implicit man: ClassManifest[T]): AnyRef = {
    val name = man.erasure.getName()
    val clazz = java.lang.Class.forName(name + "$")

    clazz.getField("MODULE$").get(clazz)
  }

  def companionUnapply1[T <: AnyRef, A](implicit man: ClassManifest[T]): T => Option[A] = {
    val comp = companion[T]
    val unapp = comp.getClass.getDeclaredMethod("unapply", man.erasure)

    { t: T => unapp.invoke(comp, t).asInstanceOf[Option[A]] }
  }

  def companionApplyUnapply1[T <: AnyRef, A](implicit man: ClassManifest[T]): (A => T, T => Option[A]) = {
    val comp = companion[T]
    val unapp = comp.getClass.getDeclaredMethod("unapply", man.erasure)
    val app = comp.getClass.getDeclaredMethod("apply", classOf[AnyRef])

    (
      { a: A => app.invoke(comp, a.asInstanceOf[AnyRef]).asInstanceOf[T] },
      { t: T => unapp.invoke(comp, t).asInstanceOf[Option[A]] }
    )
  }

  def companionUnapply2[T <: AnyRef, A, B](implicit man: ClassManifest[T]): T => Option[(A, B)] = {
    val comp = companion[T]
    val unapp = comp.getClass.getDeclaredMethod("unapply", man.erasure)

    { t: T => unapp.invoke(comp, t).asInstanceOf[Option[(A, B)]] }
  }

  import scala.collection.JavaConversions._
  def companionApplyUnapply2[T <: AnyRef, A, B](implicit man: ClassManifest[T]): ((A, B) => T, T => Option[(A, B)]) = {
    val comp = companion[T]
    val unapp = comp.getClass.getDeclaredMethod("unapply", man.erasure)
    println(comp.getClass.getDeclaredMethods.toList)
    val app = comp.getClass.getDeclaredMethod("apply", classOf[AnyRef], classOf[AnyRef])

    (
      { (a: A, b: B) => app.invoke(comp, a.asInstanceOf[AnyRef], b.asInstanceOf[AnyRef]).asInstanceOf[T] },
      { t: T => unapp.invoke(comp, t).asInstanceOf[Option[(A, B)]] }
    )
  }

  def companionUnapply3[T <: AnyRef, A, B, C](implicit man: ClassManifest[T]): T => Option[(A, B, C)] = {
    val comp = companion[T]
    val unapp = comp.getClass.getDeclaredMethod("unapply", man.erasure)

    { t: T => unapp.invoke(comp, t).asInstanceOf[Option[(A, B, C)]] }
  }

  def companionApplyUnapply3[T <: AnyRef, A, B, C](implicit man: ClassManifest[T]): ((A, B, C) => T, T => Option[(A, B, C)]) = {
    val comp = companion[T]
    val unapp = comp.getClass.getDeclaredMethod("unapply", man.erasure)
    val app = comp.getClass.getDeclaredMethod("apply", classOf[AnyRef], classOf[AnyRef], classOf[AnyRef])

    (
      { (a: A, b: B, c: C) => app.invoke(comp, a.asInstanceOf[AnyRef], b.asInstanceOf[AnyRef], c.asInstanceOf[AnyRef]).asInstanceOf[T] },
      { t: T => unapp.invoke(comp, t).asInstanceOf[Option[(A, B, C)]] }
    )
  }

  def companionUnapply4[T <: AnyRef, A, B, C, D](implicit man: ClassManifest[T]): T => Option[(A, B, C, D)] = {
    val comp = companion[T]
    val unapp = comp.getClass.getDeclaredMethod("unapply", man.erasure)

    { t: T => unapp.invoke(comp, t).asInstanceOf[Option[(A, B, C, D)]] }
  }

  def companionApplyUnapply4[T <: AnyRef, A, B, C, D](implicit man: ClassManifest[T]): ((A, B, C, D) => T, T => Option[(A, B, C, D)]) = {
    val comp = companion[T]
    val unapp = comp.getClass.getDeclaredMethod("unapply", man.erasure)
    val app = comp.getClass.getDeclaredMethod("apply", classOf[AnyRef], classOf[AnyRef], classOf[AnyRef], classOf[AnyRef])

    (
      { (a: A, b: B, c: C, d: D) => app.invoke(comp, a.asInstanceOf[AnyRef], b.asInstanceOf[AnyRef], c.asInstanceOf[AnyRef], d.asInstanceOf[AnyRef]).asInstanceOf[T] },
      { t: T => unapp.invoke(comp, t).asInstanceOf[Option[(A, B, C, D)]] }
    )
  }

  case class Person(name: String, age: Int)

  def main(args: Array[String]): Unit = {
    import scala.collection.JavaConversions._

    val s = companion[Person]
    val app = s.getClass.getDeclaredMethod("apply", classOf[AnyRef], classOf[AnyRef])
    println(app.invoke(s, "hi", new java.lang.Integer(12)))
    println(s.getClass.getDeclaredMethods.mkString)
    val unapp = s.getClass.getDeclaredMethod("unapply", classOf[Person])
    println(unapp.invoke(s, Person("hi", 12)))

  }
}