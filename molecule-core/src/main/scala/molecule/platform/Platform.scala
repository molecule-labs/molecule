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
package platform

import process._
import channel.{ ROChan, RIChan, RChan, Chan }
import platform.monitor.ProcessMonitor
import stream.{ IChan, OChan }

/**
 * Encapsulate access to underlying platform resources,
 * timers, JVM threads, ....
 *
 * An external admin thread uses the platform to bootstrap components.
 *
 */
abstract class Platform extends java.util.concurrent.Executor {

  /**
   * Platform name
   */
  def name: String

  /**
   * Platform monitor
   */
  def monitor: ProcessMonitor

  /**
   * Platform scheduler
   */
  def scheduler: Scheduler

  /**
   * Execute a (non-blocking) task using a new user-level thread created by
   * this platform.
   *
   * @param task the (non-blocking) task to execute.
   * @return unit
   */
  def execute(task: Runnable): Unit =
    scheduler.mkUThread().execute(task)

  /**
   * Launch a process.
   *
   * @param process a process.
   * @param rc the return channel.
   * @return unit
   */
  final def launch[R: Message](process: Process[R], rc: ROChan[R]): Unit = {

    val p = monitor(process)

    val uthread = scheduler.mkUThread()

    p.start(uthread, rc)
  }

  /**
   * Expose a streaming channel as a local system-level channel by
   * executing its transformations over this platform.
   *
   * @param a streaming channel.
   * @return a system-level channel
   */
  final def expose[A: Message](ichan: IChan[A]): channel.IChan[A] = {
    if (ichan.isInstanceOf[stream.ichan.FrontIChan[_]]) {
      val fichan = ichan.asInstanceOf[stream.ichan.FrontIChan[_]]
      if (fichan.ichan.isInstanceOf[stream.ichan.BackIChan[_]]) {
        val bichan = fichan.ichan.asInstanceOf[stream.ichan.BackIChan[A]]
        return bichan.ichan
      }
    }
    val (i, o) = Chan.mk[A]()
    launch(ichan.connect(o))
    i
  }

  /**
   * Expose a streaming channel as a local system-level channel by
   * executing its transformations over this platform.
   *
   * @param a streaming channel.
   * @return a system-level channel
   */
  final def expose[A: Message](ochan: OChan[A]): channel.OChan[A] = {
    if (ochan.isInstanceOf[stream.ochan.BackOChan[_]]) {
      val bochan = ochan.asInstanceOf[stream.ochan.BackOChan[A]]
      return bochan.ochan
    }
    val (i, o) = Chan.mk[A]()
    launch(stream.liftIChan(i).connect(ochan))
    o
  }

  /**
   * Launch a process
   *
   * @param process a process.
   * @return the result channel
   */
  final def launch[R: Message](process: Process[R]): RIChan[R] = {
    val (ri, ro) = RChan.mk[R]()
    launch(process, ro)
    ri
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, R: Message](f: (IChan[I1]) => Process[R]): Connections1x0[I1, R] = {
    val (i1, o1) = Chan.mk[I1]()
    val rchan = launch(f(i1))
    Connections1x0(o1, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[O1: Message, R: Message](f: (OChan[O1]) => Process[R]): Connections0x1[O1, R] = {
    val (i1, o1) = Chan.mk[O1]()
    val rchan = launch(f(o1))
    Connections0x1(i1, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, O1: Message, R: Message](f: (IChan[I1], OChan[O1]) => Process[R]): Connections1x1[I1, O1, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (from_o1, o1) = Chan.mk[O1]()
    val rchan = launch(f(i1, o1))
    Connections1x1(to_i1, from_o1, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, I2: Message, R: Message](f: (IChan[I1], IChan[I2]) => Process[R]): Connections2x0[I1, I2, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (i2, to_i2) = Chan.mk[I2]()
    val rchan = launch(f(i1, i2))
    Connections2x0(to_i1, to_i2, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[O1: Message, O2: Message, R: Message](f: (OChan[O1], OChan[O2]) => Process[R]): Connections0x2[O1, O2, R] = {
    val (from_o1, o1) = Chan.mk[O1]()
    val (from_o2, o2) = Chan.mk[O2]()
    val rchan = launch(f(o1, o2))
    Connections0x2(from_o1, from_o2, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, I2: Message, O1: Message, R: Message](f: (IChan[I1], IChan[I2], OChan[O1]) => Process[R]): Connections2x1[I1, I2, O1, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (i2, to_i2) = Chan.mk[I2]()
    val (from_o1, o1) = Chan.mk[O1]()
    val rchan = launch(f(i1, i2, o1))
    Connections2x1(to_i1, to_i2, from_o1, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, O1: Message, O2: Message, R: Message](f: (IChan[I1], OChan[O1], OChan[O2]) => Process[R]): Connections1x2[I1, O1, O2, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (from_o1, o1) = Chan.mk[O1]()
    val (from_o2, o2) = Chan.mk[O2]()
    val rchan = launch(f(i1, o1, o2))
    Connections1x2(to_i1, from_o1, from_o2, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, I2: Message, O1: Message, O2: Message, R: Message](f: (IChan[I1], IChan[I2], OChan[O1], OChan[O2]) => Process[R]): Connections2x2[I1, I2, O1, O2, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (i2, to_i2) = Chan.mk[I2]()
    val (from_o1, o1) = Chan.mk[O1]()
    val (from_o2, o2) = Chan.mk[O2]()
    val rchan = launch(f(i1, i2, o1, o2))
    Connections2x2(to_i1, to_i2, from_o1, from_o2, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, I2: Message, I3: Message, R: Message](f: (IChan[I1], IChan[I2], IChan[I3]) => Process[R]): Connections3x0[I1, I2, I3, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (i2, to_i2) = Chan.mk[I2]()
    val (i3, to_i3) = Chan.mk[I3]()
    val rchan = launch(f(i1, i2, i3))
    Connections3x0(to_i1, to_i2, to_i3, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[O1: Message, O2: Message, O3: Message, R: Message](f: (OChan[O1], OChan[O2], OChan[O3]) => Process[R]): Connections0x3[O1, O2, O3, R] = {
    val (from_o1, o1) = Chan.mk[O1]()
    val (from_o2, o2) = Chan.mk[O2]()
    val (from_o3, o3) = Chan.mk[O3]()
    val rchan = launch(f(o1, o2, o3))
    Connections0x3(from_o1, from_o2, from_o3, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, I2: Message, I3: Message, O1: Message, R: Message](f: (IChan[I1], IChan[I2], IChan[I3], OChan[O1]) => Process[R]): Connections3x1[I1, I2, I3, O1, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (i2, to_i2) = Chan.mk[I2]()
    val (i3, to_i3) = Chan.mk[I3]()
    val (from_o1, o1) = Chan.mk[O1]()
    val rchan = launch(f(i1, i2, i3, o1))
    Connections3x1(to_i1, to_i2, to_i3, from_o1, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, O1: Message, O2: Message, O3: Message, R: Message](f: (IChan[I1], OChan[O1], OChan[O2], OChan[O3]) => Process[R]): Connections1x3[I1, O1, O2, O3, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (from_o1, o1) = Chan.mk[O1]()
    val (from_o2, o2) = Chan.mk[O2]()
    val (from_o3, o3) = Chan.mk[O3]()
    val rchan = launch(f(i1, o1, o2, o3))
    Connections1x3(to_i1, from_o1, from_o2, from_o3, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, I2: Message, I3: Message, O1: Message, O2: Message, R: Message](f: (IChan[I1], IChan[I2], IChan[I3], OChan[O1], OChan[O2]) => Process[R]): Connections3x2[I1, I2, I3, O1, O2, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (i2, to_i2) = Chan.mk[I2]()
    val (i3, to_i3) = Chan.mk[I3]()
    val (from_o1, o1) = Chan.mk[O1]()
    val (from_o2, o2) = Chan.mk[O2]()
    val rchan = launch(f(i1, i2, i3, o1, o2))
    Connections3x2(to_i1, to_i2, to_i3, from_o1, from_o2, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, I2: Message, O1: Message, O2: Message, O3: Message, R: Message](f: (IChan[I1], IChan[I2], OChan[O1], OChan[O2], OChan[O3]) => Process[R]): Connections2x3[I1, I2, O1, O2, O3, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (i2, to_i2) = Chan.mk[I2]()
    val (from_o1, o1) = Chan.mk[O1]()
    val (from_o2, o2) = Chan.mk[O2]()
    val (from_o3, o3) = Chan.mk[O3]()
    val rchan = launch(f(i1, i2, o1, o2, o3))
    Connections2x3(to_i1, to_i2, from_o1, from_o2, from_o3, rchan)
  }

  /**
   * Launch a process
   *
   * @param f a function that binds a process to its channels.
   * @return the connections
   */
  final def launch[I1: Message, I2: Message, I3: Message, O1: Message, O2: Message, O3: Message, R: Message](f: (IChan[I1], IChan[I2], IChan[I3], OChan[O1], OChan[O2], OChan[O3]) => Process[R]): Connections3x3[I1, I2, I3, O1, O2, O3, R] = {
    val (i1, to_i1) = Chan.mk[I1]()
    val (i2, to_i2) = Chan.mk[I2]()
    val (i3, to_i3) = Chan.mk[I3]()
    val (from_o1, o1) = Chan.mk[O1]()
    val (from_o2, o2) = Chan.mk[O2]()
    val (from_o3, o3) = Chan.mk[O3]()
    val rchan = launch(f(i1, i2, i3, o1, o2, o3))
    Connections3x3(to_i1, to_i2, to_i3, from_o1, from_o2, from_o3, rchan)
  }

  /**
   * Block the calling thread until all processes are terminated
   *
   * In practice, due to race conditions, the method may be invoked
   * before a process is launched. Therefore, for robustness one must test
   * test that processes have terminated using their return channel
   * before calling this method.
   *
   */
  @deprecated("Use `get_!` on RIChan's instead", "0.4")
  def collect() =
    monitor.await()

  /**
   * Shutdown and close all resources allocated by the platform.
   */
  def shutdownNow(): Unit =
    scheduler.shutdownNow()

  /**
   * Shutdown.
   *
   * Close all threading resources allocated by the platform.
   */
  def shutdown(): Unit = {
    monitor.await()
    scheduler.shutdown()
  }

  def log(msg: => String): Unit =
    println("INFO:" + name + "/" + msg)

  def logDbg(msg: => String): Unit =
    if (monitor.debug) println("DBG:" + name + "/" + msg)

  def logErr(msg: String): Unit =
    println("ERR:" + name + "/" + msg)

  def logWarn(msg: String): Unit =
    println("WARN:" + name + "/" + msg)

}

abstract class PlatformFactory[P <: Platform] {

  /**
   * Create a new Platform.
   *
   * By default, a platform uses as many native threads as there are hardware threads available
   * from this runtime system (@see java.lang.Runtime.availableProcessors).
   *
   * @param name the name of the platform.
   * @return a new platform
   */
  final def apply(name: String): P =
    apply(name, false)

  /**
   * Create a new Platform.
   *
   * By default, a platform uses as many native threads as there are hardware threads available
   * from this runtime system (@see java.lang.Runtime.availableProcessors).
   *
   * @param name the name of the platform.
   * @param debug turn on logging of debug information (e.g. which processes started or stopped).
   * @return a new platform
   */
  final def apply(name: String, debug: Boolean): P = {
    apply(name, Platform.defaultThreadCount, debug)
  }

  /**
   * Create a new Platform.
   *
   * @param name the name of the platform.
   * @param nbThreads the number of native threads allocated to the platform.
   * @return a new platform
   */
  final def apply(name: String, nbThreads: Int): P =
    apply(name, nbThreads, false)

  import schedulers.{ SingleThreadedScheduler, FlowParallelScheduler }

  /**
   * Create a new Platform.
   *
   * @param name the name of the platform.
   * @param nbThreads the number of native threads allocated to the platform.
   * @param debug turn on logging of debug information (e.g. which processes started or stopped).
   * @return a new platform
   */
  final def apply(name: String, nbThreads: Int, debug: Boolean): P = {
    val schedulerFactory: SchedulerFactory =
      if (nbThreads == 1)
        SingleThreadedScheduler
      else
        FlowParallelScheduler(nbThreads)

    apply(name, schedulerFactory, debug)
  }

  /**
   * Create a new Platform.
   *
   * @param name the name of the platform.
   * @param schedulerFactory the scheduler used to schedule lightweight processes on this platform
   *        (see for instance `schedulers.FlowParallelScheduler` or `schedulers.SingleThreadedScheduler`)
   * @return a new platform
   */
  final def apply(name: String, schedulerFactory: SchedulerFactory): P =
    apply(name, schedulerFactory, false)

  /**
   * Create a new Platform.
   *
   * @param name the name of the platform.
   * @param schedulerFactory the scheduler used to schedule lightweight processes on this platform
   *        (see for instance `schedulers.FlowParallelScheduler` or `schedulers.SingleThreadedScheduler`)
   * @param debug turn on logging of debug information (e.g. which processes started or stopped).
   * @return a new platform
   */
  final def apply(name: String, schedulerFactory: SchedulerFactory, debug: Boolean): P = {
    val tf = new ThreadFactory(name, true)
    newPlatform(name, new ProcessMonitor(name, debug), schedulerFactory(tf))
  }

  def newPlatform(name: String, monitor: ProcessMonitor, bind: P => Scheduler): P
}

object Platform extends PlatformFactory[Platform] {

  /**
   * Return available processors
   */
  val availableProcessors = Runtime.getRuntime().availableProcessors

  /**
   * Return available memory
   */
  def availableMemory = Runtime.getRuntime().freeMemory

  /**
   * Set the maximum allowed segment size for this platform.
   *
   * Using a large value (>1000) is likely to cause stack overflows
   * but benchmarks show that 50 is more than enough.
   */
  final val segmentSizeThreshold = 50
  //@inline final def segmentSizeThreshold = _segmentSizeThreshold

  /**
   * Maximum allowed stream complexity for this platform.
   * Using a large value (>1000) is likely to cause stack overflows
   * but benchmarks show that 50 is more than enough.
   * It is a `var` only because of benchmarks who tweak this value.
   */
  final var _complexityCutoffThreshold = 50
  @inline final def complexityCutoffThreshold = _complexityCutoffThreshold

  val defaultThreadCount =
    if (availableProcessors > 8) availableProcessors / 2 else availableProcessors

  private[molecule] val scheduledExecutor = executors.ScheduledExecutor(1)

  private class DefaultPlatform(val name: String, val monitor: ProcessMonitor)(bind: Platform => Scheduler) extends Platform {
    final val scheduler = bind(this)
  }

  def newPlatform(name: String, monitor: ProcessMonitor, bind: Platform => Scheduler): Platform =
    new DefaultPlatform(name, monitor)(bind)

}