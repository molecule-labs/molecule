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
package channel
package impl

/**
 * Many-to-one implementation
 *
 * The implementation limits contention using the following approach. Segments are written to a rendez-vous channel that
 * will immediately enqueue the segment concurrently to a N-to-1 queue. If the consumer faster than the producers, the next
 * segments will be enqueued concurrently again. Else, the next segments sitting in the rendez-vous channel will be
 * pulled sequentially by the consumer to the N-to-1 queue at the time it consumes segments already produced.
 *
 * @author Sebastien Bocq
 */
private[channel] object ManyToOneBChan {

  def apply[A: Message](maxBatchSize: Int): (IChan[A], OChanFactory[A]) = {
    assert(maxBatchSize >= 0, "Max batch size must be greater or equal to zero but got " + maxBatchSize)
    if (maxBatchSize == 0) ManyToOneBChan()
    else {
      val ch = new ManyToOneBChanImpl(new N1QueueBatch(maxBatchSize))
      (ch, OChanFactory(ch.mkOChan))
    }
  }

  def apply[A: Message](): (IChan[A], OChanFactory[A]) = {
    val ch = new ManyToOneBChanImpl(new N1QueueZero())
    (ch, OChanFactory(ch.mkOChan))
  }

  private abstract class N1Queue[A] {

    private[this] final val m = new utils.Mutex()

    protected final class Node(private[this] final var seg: Seg[A], ichan: IChan[A], final var next: Node = null) {

      def length = seg.length

      def take(n: Int): Seg[A] = {
        val (s1, s2) = seg.splitAt(n)
        seg = s2
        s1
      }

      /**
       * Invoke the continuation of the producer only when its segment is effectively consumed.
       */
      def consume(write: (Seg[A], IChan[A]) => Unit): Seg[A] = {
        ichan match {
          case n: IChan.Nil =>
          case _ => ichan.read(write)
        }
        seg
      }

      /**
       * Poison this segment and the continuation of the producer. This is called when the input
       * side of the channel is poisoned.
       */
      def poison(signal: Signal)(implicit m: Message[A]): Unit = {
        seg.poison(signal)
        ichan.poison(signal)
      }

      override def toString = seg.toString

    }

    private[this] final var LAST: Node = null
    @volatile private[this] var FIRST: Node = null

    final def enqueue(seg: Seg[A], ichan: IChan[A]): Boolean = {
      val n = new Node(seg, ichan)
      m.lock()
      if (LAST eq null) { // was empty
        LAST = n
        FIRST = n
        m.unlock()
        true
      } else {
        LAST.next = n
        LAST = n
        m.unlock()
        false
      }
    }

    final protected def dropFirst(first: Node): Unit = {
      val next = first.next
      if (next eq null) {
        m.spinLock()
        val next = first.next
        if (next == null) {
          LAST = null
          FIRST = null
          m.unlock()
        } else {
          m.unlock()
          FIRST = next
        }
      } else
        FIRST = next
    }

    final protected def setFirst(node: Node): Unit = {
      FIRST = node
    }

    final protected def getFirst: Node = FIRST

    /**
     * May return null although queue is not empty
     */
    def dequeue(continue: (Seg[A], IChan[A]) => Unit): Seg[A]

    /**
     * Return true if the queue is empty
     */
    def isEmpty: Boolean = FIRST eq null

    /**
     * Can only be called if isEmpty returns false else throws a NPE
     */
    def forceDequeue(continue: (Seg[A], IChan[A]) => Unit): Seg[A]

    /**
     * Poison the entire content of the queue.
     */
    def poison(signal: Signal)(implicit ma: Message[A]): Unit = {
      m.spinLock()
      var first = FIRST
      FIRST = null
      LAST = null
      m.unlock()
      while (first != null) {
        //println("POISON:" + first)
        first.poison(signal)
        first = first.next
      }
    }
  }

  private final class N1QueueZero[A] extends N1Queue[A] {

    def dequeue(continue: (Seg[A], IChan[A]) => Unit): Seg[A] = {
      val first = getFirst
      if ((first eq null) || (first.next eq null))
        return null
      else {
        val seg = first.consume(continue)
        val next = first.next
        setFirst(next)
        seg
      }
    }

    def forceDequeue(continue: (Seg[A], IChan[A]) => Unit): Seg[A] = {
      val first = getFirst
      val seg = first.consume(continue)
      dropFirst(first)
      seg
    }
  }

  private final class N1QueueBatch[A](maxBatchSize: Int) extends N1Queue[A] {

    @scala.annotation.tailrec
    private[this] def mkWeakSeg(node: Node, acc: Seg[A], remaining: Int, continue: (Seg[A], IChan[A]) => Unit): Seg[A] = {
      val len = node.length
      if (len > remaining) {
        val seg = node.take(remaining)
        setFirst(node)
        acc ++ seg
      } else if (node.next eq null) {
        setFirst(node)
        acc
      } else {
        val seg = node.consume(continue)
        val next = node.next
        val rem = remaining - seg.length
        if (rem == 0) {
          setFirst(next)
          acc ++ seg
        } else {
          mkWeakSeg(next, acc ++ seg, rem, continue)
        }
      }
    }

    def dequeue(continue: (Seg[A], IChan[A]) => Unit): Seg[A] = {
      val first = getFirst
      if ((first eq null) || (first.next eq null))
        return null
      else
        mkWeakSeg(first, Seg.empty, maxBatchSize, continue)
    }

    def forceDequeue(continue: (Seg[A], IChan[A]) => Unit): Seg[A] = {
      val first = getFirst
      val len = first.length
      if (len > maxBatchSize) {
        first.take(maxBatchSize)
      } else {
        val seg = first.consume(continue)
        dropFirst(first)
        seg
      }
    }
  }

  private final class ManyToOneBChanImpl[A: Message](q: N1Queue[A]) extends IChan[A] { outer =>
    private[this] final var RK: (Seg[A], IChan[A]) => Unit = null
    private[this] final var SIGNAL: Signal = null

    private[this] final val m = new utils.Mutex()

    final def mkOChan(): OChan[A] = {
      val (i, o) = Chan.mk()
      i.read(write)
      o
    }

    private[this] final val write: (Seg[A], IChan[A]) => Unit = { (seg, ichan) =>
      //println("enqueue:" + seg)
      if (q.enqueue(seg, ichan)) {
        m.spinLock() // Pin lock is fine because at most one producer at a time comes here.
        if (RK != null) {
          if (!q.isEmpty) {
            val rk = RK
            RK = null
            // Since this is not a reentrant lock, it is important we do not call continuations 
            // while holding the lock!
            m.unlock()
            val seg = q.forceDequeue(write)
            //println("DEQUEUE0:" + seg)
            rk(seg, this)
          } else
            m.unlock()
        } else if (SIGNAL != null) {
          m.unlock()
          //println("POISON+")
          q.poison(SIGNAL)
        } else
          m.unlock()
      }
    }

    def read(rk: (Seg[A], IChan[A]) => Unit): Unit = {
      //print("*")
      val seg = q.dequeue(write)
      if (seg == null) {
        m.spinLock()
        if (q.isEmpty) {
          RK = rk
          m.unlock()
        } else {
          m.unlock()
          //println("DEQUEUE+:" + seg)
          rk(q.forceDequeue(write), this)
        }
      } else {
        //println("DEQUEUE:" + seg)
        rk(seg, this)
      }
    }

    final def poison(signal: Signal): Unit = {
      //println("POISON")
      if (SIGNAL == null) {
        m.lock()
        SIGNAL = signal
        if (RK != null) {
          val rk = RK
          RK = null
          m.unlock()
          rk(NilSeg, IChan.empty(signal))
        } else
          m.unlock()
        q.poison(signal)
      }
    }

  }
}