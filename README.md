# Molecule

A concurrent programming library implementing monadic and streaming I/O in Scala.

- [Main Features](#main-features)
- [Example](#example)
- [Installing](#installing)
- [Building From Sources](#building-from-sources)
- [Running The Examples](#running-the-examples)

## Main Features

- User-level threading model with low-overhead context switches on unmodified JVM's.
- High-performance and convenient stream processing primitives that batch data transparently.
- High-performance protocol design on top of TCP or UDP sockets using incremental combinator parsers (ala [AttoParsec](http://hackage.haskell.org/packages/archive/attoparsec/0.8.0.2/doc/html/Data-Attoparsec.html)).
- Exceptions and graceful termination handling.
- Higher maintainability, reliability and flexibility compared applications written against event-driven interfaces in plain Java.

A paper explaining the rationale and the design principles of Molecule is available [here](https://github.com/molecule-labs/molecule/publications/OOPSLA_2012/)

## Example

_Note: many other examples are available for study in the `molecule-*-examples` directories._

This example will walk you through the implemention an process that says hello to the end user using the command line and then expose it as a Telnet server using Molecule's NIO interfaces and its incremental parser combinators. 

### Interacting On The Command Line

Here is how a process that interracts on the command line is defined and then launched.

```scala
import molecule._
import molecule.io._

object HelloYou extends ProcessType1x1[String, String, Unit] {

  def main(in: Input[String], out: Output[String]) = for {
    _    <- out.write("What is your name?")
    name <- in.read()
    _    <- out.write("Hello " + name + "!")
  } yield ()

  import molecule.platform.Platform
  import molecule.channel.Console

  def main(args: Array[String]): Unit = {
    // Create an execution platform
    val platform = Platform("hello-you")

    // Launch an instance of the HelloYou component on the platform
    // and block the main thread with `get_!` until it terminated.
    platform.launch(HelloYou(Console.stdinLine, Console.stdoutLine)).get_!()
  }
}
```

Before defining a new process type, we must import two packages. The first one imports molecule's main package. The second imports the abstract monadic process type `ProcessTypeixj` with various useful monadic combinators defined as value members in the package object `molecule/io/package.scala`. The behavior of a process type is specified by defining the `main` method of an abstract class patterned after function types in Scala:

```scala
abstract class ProcessTypeixj[I1, ... , Ii, O1, ..., Oj, R] {

  final def apply(i1: IChan[I1], ..., oj: IChan[B]):Process[R] = ...

  protected def main(i1:Input[I1], ..., oj:Output[Oj]):IO[R]

}
```

The abstract class is parameterized by the type `Ii` and `Oj` of the input and output channel interfaces passed as argument to the `main` method, followed by the result type `R`. The result 'R' can be retrieved by its parent process once it terminates. Since it inherits form the 'ProcessType1x1[String, String, Unit]' class, the process type `HelloYou` is a factory of process instances `Process` that use one input of type `String`, one input of type `String` and terminates with a result of type `Unit`. 

The behavior of processes of this type is defined by the for-comprehension in its main method. 

```scala
  def main(in: Input[String], out: Output[String]) = for {
    _    <- out.write("What is your name?")
    name <- in.read()
    _    <- out.write("Hello " + name + "!")
  } yield ()
```

It prompts for a name on its output, reads the name on its input, says hello on its output and then returns `()`.

Since process types implement an 'apply' method in their base class, we can use them as a factories to create lightweight process instances of type `Process`. Here, a new instance of `HelloYou` is created by passing it channel interfaces of type `IChan[String]` and `OChan[String]`. It is the Platform class that will invoke this function to create a _running_ instance of a process. The interface of a `Platform` is the following:

```scala
abstract class Platform {

  final def launch[R: Message](process: Process[R]): RIChan[R] = {

}
```

Where `RIChan` is the type of a channel that outputs a single message, which is very similar to a `Future` in `java.util.concurrent`.

The process type we just defined can then be tested on the command line by applying it to the standard `Console.stdinLine` and `Console.stdoutLine` channels, which are defined in the 'channel' package. The `stdinLine` input channel, of type IChan[String], streams each lines typed on the standard input. The `stdoutLine` output channel, of type `OChan[String]`, does the reverse and prints each string it receives in consecutive lines on the standard output. Therefore, to launch an instance of the `HellYou` process on the command line, one just needs apply its factory method to `stdinLine` and `stdoutLine` and then pass the result, which is a `Process` to the `launch` method of a platform, which will execute the process:

```scala
  def main(args: Array[String]): Unit = {
  
    val platform = Platform("hello-you")
    platform.launch(HelloYou(Console.stdinLine, Console.stdoutLine)).get_!()
  }
```

Since the process instance is executed asynchronously, the native thread must block until the process has terminated otherwise the application would exit immediately, before someone has the time to type its name. A native thread can wait for the termination of a process using the `get_!` method `future` on the result channel. Here, the it will return `()`.

### Exposing Processes Over Telnet

The following guides you into creating you own Telnet servlet container using Molecule's NIO layer. The container instantiates one lightweight processes instance each time a Telnet client connects to it. The Telnet protocol is not as simple as it seems. For simplicity, we will filter out only simple IAC commands, those that start with the `IAC` byte followed by 1 byte identifying the operation, and another byte as its option.

The object below declares a parser that parses telnet content from a `ByteBuffer` stream, which contains either some binary data or a telnet command. Readers not familiar with parser combinators are invited to look at this excellent introduction by Daniel Spiewak, which can be found [ here](http://www.codecommit.com/blog/scala/the-magic-behind-parser-combinators).

```scala
import molecule.parsers.bytebuffer._

object TelnetLineAdapter {

  val IAC = 255.toByte

  abstract class TelnetMsg
  case class Data(cb: ByteBuffer) extends TelnetMsg
  case class Command(b1: Byte, b2: Byte) extends TelnetMsg {
    override def toString() = "Command(" + unsigned(b1) + "," + unsigned(b2) + ")"
  }

  lazy val telnetMsg: Parser[ByteBuffer, TelnetMsg] = data | command

  val data = splitAt(IAC) ^^ { Data(_) }

  val command = (IAC ~ byteArray(2)) ^^ {
    case _ ~ arr => Command(arr(0), arr(1))
  }

}
```

The `splitAt` parser split each `ByteBuffer` that it receives at the position where the `IAC` command occurs or fails if the first byte of the received `ByteBuffer` matches the condition. Using the `telnetMsg` parser, we can now create a process type adapter that uses `ByteBuffer` as input and output to filter out byte buffers that start with an `IAC` command using the `collect` streaming primitive. The resulting stream of `ByteBuffer`s is then converted to a stream of `CharBuffer`s and then parsed line by line to the adapted process, like this:


```scala
abstract class TelnetLineAdapter[R: Message](ptype: ProcessType1x1[String, String, R]) extends ProcessType1x1[ByteBuffer, ByteBuffer, R] {
  import molecule.parsers.charbuffer
  import java.nio.CharBuffer

  def main(in: Input[ByteBuffer], out: Output[ByteBuffer]) =
    handover {
      ptype(
        in.parse(telnetMsg).collect {
          case Data(bb) => bb
        }.map(decode("US-ASCII")).parse(charbuffer.line(2048)),
        out.map(encode("US-ASCII")).map { s: String => CharBuffer.wrap(s.replaceAll("\n", "\r\n") + "\r\n") }
      )
    }
}
```

Now, we are ready to expose the `HelloYou` process to Telnet connections using Molecule NIO, like this:


```scala
import molecule.nio._

val HelloYouTelnet = new TelnetLineAdapter(HelloYou)
val ns = NetSystem(Platform("hello-you"))
ns.launchTcpServer("localhost", 8888, HelloYouTelnet)
```

The `launchTcpServer` method of a `NetSystem`, launches a new instance of the adapted `HelloYou` process type each time it accepts a new TCP connection on the specified socket address. Each instance is connected to the input and output streams of a socket, which happen to carry `ByteBuffer`. The underlying socket, configured in non-blocking mode, will be automatically closed once both channels are closed, that is when the instance of the `HelloYou` process terminates.

The nice thing about this server is that it can handle efficiently several thousands of Telnet sessions in just one megabyte of memory, and it can expose any process type as long as it adapted to take streams of `ByteBuffer` as input and output. As an exercise, one could try to port this example in plain Java using JBoss Netty's library to compare it to this novel approach.

## Installing

Molecule is available on the Sonatype OSS Maven repository (which is mirrored on the central Maven repository as well):

	group id: com.github.molecule-labs
	artifact ids: molecule-core_2.9.3, molecule-io_2.9.3, molecule-parsers_2.9.3, molecule-net_2.9.3
	version: 0.5

Alternatively you can download the Jar files directly from Sonatype:

- [molecule-core.jar](https://oss.sonatype.org/content/groups/public/com/github/molecule-labs/molecule-core_2.9.3/0.5/molecule-core_2.9.3-0.5.jar)
- [molecule-io.jar](https://oss.sonatype.org/content/groups/public/com/github/molecule-labs/molecule-io_2.9.3/0.5/molecule-io_2.9.3-0.5.jar)
- [molecule-parsers.jar](https://oss.sonatype.org/content/groups/public/com/github/molecule-labs/molecule-parsers_2.9.3/0.5/molecule-parsers_2.9.3-0.5.jar)
- [molecule-net.jar](https://oss.sonatype.org/content/groups/public/com/github/molecule-labs/molecule-net_2.9.3/0.5/molecule-net_2.9.3-0.5.jar)

## Building From Sources

Using [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup):

	> git clone https://github.com/molecule-labs/molecule.git
	> cd molecule
	> sbt collect-jar

## Running the Examples

Right now, the easiest way is to checkout the sources and run them from your favorite IDE with the Scala plugin installed.
