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

abstract class IOSelector {

  protected[net] def threadId: Long

  protected[net] def schedule(runnable: Runnable): Unit

  protected[net] def registerAccept(s: TcpAcceptChannel)
  protected[net] def clearAccept(s: TcpAcceptChannel)

  protected[net] def registerConnect(s: TcpConnectChannel)
  protected[net] def clearConnect(s: TcpConnectChannel)

  protected[net] def registerWrite(s: Socket[_])
  protected[net] def clearWrite(s: Socket[_])

  protected[net] def registerRead(s: Socket[_])
  protected[net] def clearRead(s: Socket[_])

  def shutdown(): Unit
  def shutdownNow(): Unit
}

object IOSelector {

  val threadFactory = new platform.ThreadFactory("selector-pool", true)

  // private[this] val default:IOSelector = new IOSelectorImpl(threadFactory)

  import java.io.IOException
  import java.nio.channels.spi.SelectorProvider
  import java.nio.channels.{ SelectionKey, SocketChannel => JSocketChannel }
  import java.util.concurrent.ThreadFactory

  def apply(): IOSelector = new IOSelectorImpl(threadFactory)

  private[this] class IOSelectorImpl(tf: ThreadFactory) extends IOSelector with Runnable { lock =>

    // The selector we'll be monitoring
    val selector = SelectorProvider.provider().openSelector();

    def registerAccept(achan: TcpAcceptChannel) =
      regOp(achan, SelectionKey.OP_ACCEPT)

    def clearAccept(achan: TcpAcceptChannel) =
      deregOp(achan, SelectionKey.OP_ACCEPT)

    def registerRead(s: Socket[_]) =
      regOp(s, SelectionKey.OP_READ)

    def clearRead(s: Socket[_]) =
      deregOp(s, SelectionKey.OP_READ)

    def registerWrite(s: Socket[_]) =
      regOp(s, SelectionKey.OP_WRITE)

    def clearWrite(s: Socket[_]) =
      deregOp(s, SelectionKey.OP_WRITE)

    def registerConnect(cchan: TcpConnectChannel) =
      regOp(cchan, SelectionKey.OP_CONNECT)

    def clearConnect(cchan: TcpConnectChannel) =
      deregOp(cchan, SelectionKey.OP_CONNECT)

    /**
     * Register an operation
     */
    def regOp(channel: Channel, op: Int) = {
      try {
        //println(System.identityHashCode(channel) + ":regop:" + opToString(op))
        val socketChannel = channel.niochan
        val key_ = socketChannel.keyFor(selector)
        val key =
          if (key_ == null)
            socketChannel.register(selector, 0, channel)
          else
            key_
        key.attach(channel) // Same channel may be passed from a TcpConnectChan to a Socket object
        val ops = key.interestOps()
        key.interestOps(ops | op)
      } catch {
        case t: java.nio.channels.CancelledKeyException =>
        // Ignore, channel has been poisoned in the meantime
        case t =>
          System.err.println(t)
          System.err.println(t.getStackTraceString)
          channel.shutdown()
      }
    }

    /**
     * Deregister an operation
     */
    def deregOp(channel: Channel, op: Int) = {
      //println(System.identityHashCode(channel) + ":deregop:" + opToString(op))
      val socketChannel = channel.niochan
      var key = socketChannel.keyFor(selector)
      /**
       * In case a socket I/O is derigestered immediately
       * nothing is ever registered, that is why we must check
       * for null.
       * In other cases the dereg command may only arrive
       * after a socket is closed and therefore is not valid
       * anymore
       */
      if (key != null && key.isValid) {
        val ops = key.interestOps()
        key.interestOps(ops & ~op)
      }
    }

    var taskQueue = List.empty[Runnable]

    @volatile
    var running = false

    // Kick the kernel
    @volatile
    var thread = tf.newThread(this);
    thread.start()

    val threadId = thread.getId()

    def shutdownNow() =
      shutdownNow(EOS)

    def shutdownNow(signal: Signal) =
      if (thread != null) {
        val moribund = thread
        thread = null
        moribund.interrupt
      }

    override def shutdown() =
      schedule(new Runnable { def run() = shutdown(EOS) })

    def shutdown(signal: Signal) =
      if (thread != null) {
        //val moribund = thread
        thread = null
        //moribund.interrupt
        selector.wakeup()
      }

    import scala.collection.JavaConversions._
    def cleanup(signal: Signal) {
      val keys = selector.keys().toList
      keys foreach { k =>
        k.attachment match {
          case ch: Channel =>
            try ch.shutdown() catch { case _ => }
          case _ => //ignore
        }
      }
    }

    // This is always called by another thread
    def schedule(task: Runnable) = {
      lock.synchronized {
        taskQueue = task :: taskQueue
      }
      if (!running) selector.wakeup()
    }

    final def runTasks = {
      var tasks = lock.synchronized {
        val tmp = taskQueue
        taskQueue = Nil
        tmp
      }

      while (!tasks.isEmpty) {
        tasks.head.run()
        tasks = tasks.tail
      }
    }

    def run() {
      while (thread != null || !selector.keys.isEmpty || synchronized { !taskQueue.isEmpty }) {
        try {

          runTasks
          running = false

          runTasks

          // Wait for an event one of the registered channels
          selector.select();

          running = true
          // Iterate over the set of keys for which events are available
          val selectedKeys = selector.selectedKeys().iterator();
          while (selectedKeys.hasNext()) {
            val key = selectedKeys.next().asInstanceOf[SelectionKey]
            selectedKeys.remove();

            if (key.isValid()) {
              val ops = key.readyOps()
              // Check what event is available and deal with it
              if ((ops & (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) != 0) {
                val s = key.attachment().asInstanceOf[Socket[_]]

                if ((ops & SelectionKey.OP_READ) != 0)
                  s.input.readReady() // may close socket
                if ((ops & SelectionKey.OP_WRITE) != 0)
                  s.output.writeReady()

              } else if ((ops & SelectionKey.OP_ACCEPT) != 0) {
                val achan = key.attachment().asInstanceOf[TcpAcceptChannel]
                achan.acceptReady()
              } else if ((ops & SelectionKey.OP_CONNECT) != 0) {
                key.interestOps(0) // Reset, we don't want to connect twice
                val achan = key.attachment().asInstanceOf[TcpConnectChannel]
                achan.connectReady()
              }
            }
          }
        } catch {
          case e: Exception =>
            e.printStackTrace();
        }
      } // After while loop
      cleanup(EOS)
    }
  }

}