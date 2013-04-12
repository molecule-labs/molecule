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

package molecule.examples.io.misc

import molecule._
import molecule.io._
import molecule.platform._

object ParTest {

  val p = Platform("partest", debug = true)

  object Dummy extends ProcessType0x0[Unit] {
    def main() = IO()
  }

  object Supervisor extends ProcessType0x0[Unit] {
    def main() = parl_((1 to 1000).map(i => launch(Dummy()).get()))
  }

  def main(args: Array[String]) {
    p.launch(Supervisor()).get_!()
  }
}