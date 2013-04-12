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
package stream
package ichan
package mutable

/**
 * @author Sebastien Bocq
 */
abstract class TestableIChan[A]
  extends StatefulIChan[Seg[A], A] with ichan.TestableIChan[A]

object TestableIChan {

  private[mutable] def apply[A: Message](src: IChan[A]): TestableIChan[A] =
    new TestableIChanImpl(src)

  private[this] final class TestableIChanImpl[A: Message](
      private[this] final var _src: IChan[A]) extends TestableIChan[A] {

    private[this] final var cache: Option[Seg[A]] = None

    def poison(signal: Signal) = synchronized {
      _src.poison(signal)
      _src = NilIChan(signal)
      state.poison(signal)
      cache = None
      _K = null
    }

    def complexity = synchronized {
      _src.complexity
    }

    def state = synchronized {
      cache match {
        case None => NilSeg
        case Some(seg) => seg
      }
    }

    // Continuations can be hotSwapped from test -> read or test -> test
    // This blows the heap with 'merge' operation if K not set to null. 
    // See https://issues.scala-lang.org/browse/SI-5367
    private[this] final var _K: Either[(UThread, IChan[A] => Unit), (UThread, (Seg[A], IChan[A]) => Unit)] = null

    private[this] final val K: (Seg[A], IChan[A]) => Unit = { (seg, next) =>
      val action: () => Unit = synchronized {
        if (_K == null)
          utils.NOOP0; //poisoned in the meantime
        else {
          val k = _K
          _K = null
          k match {
            case Left((t, testK)) =>
              cache = Some(seg)
              _src = next
              () => t.submit(testK(this))
            case Right((t, readK)) =>
              () => t.submit(readK(seg, next))
          }
        }
      }
      action()
    }

    def read(t: UThread, ready: (Seg[A], IChan[A]) => Unit) = {
      val action: () => Unit = synchronized {
        if (cache.isDefined) {
          () => ready(cache.get, _src)
        } else {
          if (_K == null) {
            () => _src.read(t, ready)
          } else {
            _K match {
              case Left(_) =>
                _K = Right((t, ready))
                utils.NOOP0
              case _ =>
                throw new channel.ConcurrentReadException
            }
          }
        }
      }
      action()
    }

    def test(t: UThread, ready: IChan[A] => Unit): Unit = {
      val action: () => Unit = synchronized {
        if (cache.isDefined) {
          () => ready(this)
        } else {
          if (_K == null) {
            _K = Left((t, ready))
            () => _src.read(t, K)
          } else {
            _K match {
              case Left(_) =>
                _K = Left((t, ready))
                utils.NOOP0
              case _ =>
                throw new channel.ConcurrentReadException
            }
          }
        }
      }
      action()
    }

    def add[B: Message](transformer: Transformer[A, B]): IChan[B] =
      transformer match {
        case TesterT() => this.asInstanceOf[IChan[B]]
        case _ =>
          transformer(this)
      }

  }

}