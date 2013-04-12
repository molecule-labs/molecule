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

package molecule.test.io

import molecule._
import platform.Platform
import io._

// TODO: Move this to test
object ParallelShutdown {
  
  object ParallelShutdown1 extends ProcessType0x0[Boolean] {
    def main() = 
      ((shutdown(true) >> IO()) |*| (shutdown(true) >> IO())) >> IO(false) 
  }

  object ParallelShutdown2 extends ProcessType0x0[Boolean] {
    def main() = 
      ((sleep(1000) >> raise(EOS) >> IO()) |*| (shutdown(true) >> IO())).orCatch {
        case _ => shutdown(false)
      } >> IO(false)
  }

  object ParallelShutdown3 extends ProcessType0x0[Boolean] {
    def main() = 
      ((shutdown(true) >> IO()) |*| (sleep(1000) >> raise(EOS) >> IO())).orCatch {
        case _ => shutdown(false)
      } >> IO(false) 
  }

  object ParallelShutdown4 extends ProcessType0x0[Boolean] {
    def main() = 
      ((sleep(1000) >> shutdown(true) >> IO()) |*| sleep(500)) >> IO(false)  
  }

  object ParallelShutdown5 extends ProcessType0x0[Boolean] {
    def main() = 
      ((sleep(1000) >> shutdown(true) >> IO()) |*| (sleep(1500) >> raise(EOS) >> IO())).orCatch {
        case _ => shutdown(false)
      } >> IO(false) 
  }

  object ParallelShutdown6 extends ProcessType0x0[Boolean] {
    def main() = 
       (sleep(1000) |*| (sleep(1000) >> shutdown(true) >> IO())) >> IO(false) 
  }

  def main(args : Array[String]) : Unit = {
    val platform = Platform("parallel-shutdown", debug = true)
    
    assert(platform.launch(ParallelShutdown1()).get_!)
    assert(platform.launch(ParallelShutdown2()).get_!)
    assert(platform.launch(ParallelShutdown3()).get_!)
    assert(platform.launch(ParallelShutdown4()).get_!)
    assert(platform.launch(ParallelShutdown5()).get_!)
    assert(platform.launch(ParallelShutdown6()).get_!)
    
  }
}
