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
package io.impl

/**
 * "Simple" continuation monad (called Responder in Scala standard lib).
 * (See either in companion object)
 */
private[io] final class Promise[+A](val deliver: (A => Unit) => Unit) {

  def flatMap[B](f: A => Promise[B]): Promise[B] = new Promise[B](
    kb => deliver(a => f(a).deliver(kb))
  )

  def map[B](f: A => B): Promise[B] = new Promise[B](
    kb => deliver(a => kb(f(a)))
  )

  @inline
  def >>\[B](f: A => Promise[B]): Promise[B] = flatMap(f)

  @inline
  def >>[B](f: => Promise[B]): Promise[B] = flatMap(_ => f)

  def filter(p: A => Boolean): Promise[A] = new Promise[A](
    k => deliver(a => if (p(a)) k(a) else sys.error("filter predicate not matched"))
  )

}

private[io] object Promise {

  private[this] final val unit: Promise[Unit] = new Promise[Unit](k => k())

  def apply(): Promise[Unit] = unit

  def unless(b: Boolean)(p: => Promise[Unit]): Promise[Unit] = if (b) p else unit

  /**
   * Select result of either promise based on the
   * first available or randomly if none is available.
   * !!!The promise not selected is discarded!!!
   *
   */
  def either[A, B](left: Promise[A], right: Promise[B]): Promise[Either[A, B]] = {
    val r = utils.random.nextInt(2)
    either(r, left, right)
  }

  def either[A, B](random: Int, left: Promise[A], right: Promise[B]): Promise[Either[A, B]] =
    new Promise[Either[A, B]]({ k =>
      var pending = true
      if (random == 0) {
        left.deliver(a => if (pending) {
          pending = false
          k(Left(a))
        })
        if (pending) {
          right.deliver(b => if (pending) {
            pending = false
            k(Right(b))
          })
        }
      } else {
        right.deliver(b => if (pending) {
          pending = false
          k(Right(b))
        })
        if (pending) {
          left.deliver(a => if (pending) {
            pending = false
            k(Left(a))
          })
        }
      }
    })

  def parl_[A, That](ps: Iterable[Promise[_]]): Promise[Unit] = new Promise[Unit]({ k =>

    val tot = ps.size
    var count = 0
    val it = ps.iterator

    val update: Any => Unit = { _ =>
      count += 1
      if (count == tot)
        k()
    }

    while (it.hasNext) {
      it.next().deliver(update)
    }

  })

  final def parlOptim_[A, That](ps: Iterable[A], f: A => Promise[_]): Promise[Unit] = new Promise[Unit]({ k =>

    val tot = ps.size
    var count = 0
    val it = ps.iterator

    val update: Any => Unit = { _ =>
      count += 1
      if (count == tot)
        k()
    }

    while (it.hasNext) {
      f(it.next()).deliver(update)
    }

  })
}
