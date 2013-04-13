# Molecule

A concurrent programming library combining monadic and streaming I/O in Scala.

By releasing Molecule as open source [Alcatel-Lucent](http://www.alcatel-lucent.com/)/[Bell Labs](http://www.bell-labs.com) is supporting research in easing the industry's transition to network function virtualization on cloud computing platforms.

- [Main Features](#main-features)
- [Example](#example)
- [Installing](#installing)
- [Building From Sources](#building-from-sources)
- [Running The Examples](#running-the-examples)

## Main Features

- User-level threading model with low-overhead context switches on unmodified JVM's.
- Type-safe communication channels.
- High-performance and convenient stream processing primitives that batch data transparently.
- Incremental combinator parsers that work over non-blocking sockets (ala [AttoParsec](http://hackage.haskell.org/packages/archive/attoparsec/0.8.0.2/doc/html/Data-Attoparsec.html)).
- Exceptions and graceful termination handling.
- Higher maintainability, reliability and flexibility compared to applications written against callback interfaces in plain Java.

Both the [paper](https://github.com/molecule-labs/molecule/tree/docs/publications/OOPSLA_2012) explaining the rationale and the design principles of Molecule, and the [latest API documentation](http://molecule-labs.github.io/molecule) are available online.

## Example

_Note: many other examples are available for study in the `molecule-*-examples` directories._

This example will walk you through the implementation of a simple process type. First, we show how instances of this process type can interact with the command line. Then, we show how to bind instances to Telnet sessions by implementing the minimal support for the Telnet protocol over binary streams using Molecule's NIO interfaces and incremental parser combinators. 

### Interacting On The Command Line

Here is how a process that interacts on the command line is defined and then launched.

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

    // Launch an instance of HelloYou on the platform
    // and block the main thread with `get_!` until it is terminated.
    platform.launch(HelloYou(Console.stdinLine, Console.stdoutLine)).get_!()
  }
}
```

Before defining a new process type, we must import two packages. The first one imports molecule's main package. The second imports the abstract monadic process type with various useful monadic combinators defined as value members in the [`io`](http://molecule-labs.github.io/molecule/#molecule.io.package) package object. Process types are patterned after function types in Scala:

```scala
abstract class ProcessTypeixj[I1, ... , Ii, O1, ..., Oj, R] {

  final def apply(i1: IChan[I1], ..., oj: IChan[Oj]):Process[R] = ...

  protected def main(i1:Input[I1], ..., oj:Output[Oj]):IO[R]

}
```

The abstract class is parameterized by the type `Ii` and `Oj` of the input and output channel interfaces of a process followed by its result type `R`. The `apply` method is used as a factory method to create lightweight processes. Since `HelloYou` inherits form [`ProcessType1x1[String, String, Unit]`](http://molecule-labs.github.io/molecule/#molecule.io.ProcessType1x1), the process type `HelloYou` is a factory of process instances that use one input of type `String`, one output of type `String` and terminates with a result of type `Unit`. All the processes it creates will share the same behavior, which is defined by the implementation of its `main` method: 

```scala
  def main(in: Input[String], out: Output[String]) = for {
    _    <- out.write("What is your name?")
    name <- in.read()
    _    <- out.write("Hello " + name + "!")
  } yield ()
```

It prompts for a name on its output, reads the name on its input, says hello on its output and then returns `()`.

We can then create a `HelloYou` process attached to the command line by "applying" its process type to the standard `Console.stdinLine` and `Console.stdoutLine` channels, which are defined in the [channel](http://molecule-labs.github.io/molecule/#molecule.channel.Console$) package: 

```scala
  def main(args: Array[String]): Unit = {
  
    val platform = Platform("hello-you")
    platform.launch(HelloYou(Console.stdinLine, Console.stdoutLine)).get_!()
  }
```

The `stdinLine` input channel, of type `IChan[String]`, streams each lines typed on the standard input. The `stdoutLine` output channel, of type `OChan[String]`, does the reverse and prints each string it receives on consecutive lines on the standard output. 

A [`Platform`](http://molecule-labs.github.io/molecule/#molecule.platform.Platform) creates the user-level threads that execute processes over a handful number of native threads. This number is configurable and matches by default the number of cores available on the underlying hardware (see `Platform` [factory methods](http://molecule-labs.github.io/molecule/#molecule.platform.Platform$)). The launch method is declared like this:

```scala
abstract class Platform {

  final def launch[R: Message](process: Process[R]): RIChan[R] = {

}
```

 The type [`RIChan`](http://molecule-labs.github.io/molecule/#molecule.channel.RIChan) is the type of channels that deliver a single message, a bit like a `Future` in `java.util.concurrent`. Since the process instance is executed asynchronously, the native thread must block until the process has terminated, otherwise the application would exit immediately before someone has the time to type its name. This is done using the `get_!` method of the result channel, which blocks the main thread until the process returns its result `()`.

### Exposing Processes Over Telnet

We will now create a "Telnet servlet container" that instantiates a new process each time a Telnet client connects to it. For simplicity, we will just filter out [initial Telnet negotiation commands](http://tools.ietf.org/html/rfc854) - those that start with the `IAC` byte followed by 1 byte identifying the operation and a second byte indicating the option. To do so, we create an incremental binary parser that we will use to parse Telnet messages from `ByteBuffer` streams read on non-blocking TCP sockets:

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

_Note: readers not familiar with parser combinators are invited to look at [this](http://www.codecommit.com/blog/scala/the-magic-behind-parser-combinators) excellent introduction by Daniel Spiewak._

In case of Telnet, the binary stream carries either some binary Data or a Telnet Command that starts with the `IAC` byte. The `splitAt` parser splits each `ByteBuffer` that it receives at the position where the `IAC` command occurs or fails if the first byte of the received `ByteBuffer` matches `IAC`. Using the `telnetMsg` parser, we can now create a process type adapter that adapts process types that interact over string channels into process types that interact over raw byte buffer channels:

```scala
abstract class TelnetLineAdapter[R: Message](ptype: ProcessType1x1[String, String, R]) 
extends ProcessType1x1[ByteBuffer, ByteBuffer, R] {
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

Telnet commands are filtered out from the byte buffer input stream using the `collect` streaming primitive. The resulting stream is then converted into a stream of strings through ASCII decoded `CharBuffer`s, which are then parsed into lines of maximum 2048 characters. Lines sent on the output are wrapped into character buffers and then encoded into ASCII byte buffers (output channels being contravariant, transformations must be read from right to left).

Now, we are ready to expose `HelloYou` processes over individual Telnet connections using Molecule's NIO layer, like this:

```scala
import molecule.nio._

val HelloYouTelnet = new TelnetLineAdapter(HelloYou)
val ns = NetSystem(Platform("hello-you"))
ns.launchTcpServer("localhost", 8888, HelloYouTelnet)
```

The `launchTcpServer` method of a [`NetSystem`](http://molecule-labs.github.io/molecule/#molecule.net.NetSystem), launches a new instance of the adapted `HelloYou` process type each time it accepts a new TCP connection on the specified socket address. Each process will be connected to the byte buffer input and output streams of the socket connected to the client. This socket, configured in non-blocking mode, will be automatically closed once both channels are closed. This occurs as soon as the process terminates thanks to the automatic resource management implemented by monadic processes. The nice thing about this server is that it can handle efficiently more than one thousands Telnet sessions in one megabyte of memory without blocking any native thread. Also, now that we created this adapter, we can reuse it to expose any interactive process over Telnet as long as this process interacts line by line over string channels. 

**Note:** _Similar examples can be found in [`molecule-io-example`](https://github.com/molecule-labs/molecule/blob/master/molecule-io-examples/src/main/scala/molecule/examples/io/EchoYou.scala) and [`molecule-net-examples`](https://github.com/molecule-labs/molecule/blob/master/molecule-net-examples/src/main/scala/molecule/examples/net/echoyou/EchoYouTelnet.scala). See section ["Running The Examples"](#running-the-examples) for instructions on how to run these examples._

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
