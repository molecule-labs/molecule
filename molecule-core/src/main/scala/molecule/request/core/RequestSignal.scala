package molecule
package request.core

import stream.OChan

case class RequestSignal(ochan: OChan[_], signal: Signal) extends Signal
