# Stream Processing with Observables

* [Prev](index.md)

For the purposes of this talk we are going to focus on using an **observable** to
build a system which uses backpressure to respond to overload situations. This is
the solution provided as part of the reactive extensions specification and is, as
a result, available in a wide variety of languages.

So, what is an `Observable`? At its core it's basically an interface for an `Observer`
to subscribe to a data source along with some contractual details about how that
interface will be used.

## Creating an Observer

In general, an `Observer` has a few important methods:

1. An `onNext`  method called for each data element in the `Observable`. In reactive implementations
   that support backpressure (such as Monix) the return of this method indicates the synchronous or
   asynchronous completion of processing on the work item with success or an error.
2. An `onComplete` method that is called when the `Observable` contains no more data elements.
3. An `onError` method called when the `Observable` encountered some kind of error is encountered
   which results in the stream terminating.

The interface looks something like this:

```scala
import monix.execution.Ack
import monix.reactive.Observer
import scala.concurrent.Future

final class MyObserver[-T] extends Observer[T] {
  override def onNext(elem: T): Future[Ack] = {
    println(elem)
    Ack.Continue
  }

  override def onError(ex: Throwable): Unit =
    println(ex.getMessage)
    
  override def onComplete(): Unit =
    println("Finished!")
}
```

See: [Monix Documentation](https://monix.io/docs/3x/reactive/observable.html#observable-contract)

## The Observer Contract

Now, it's useful to understand how these methods will be called. There are several rules to be followed
when interacting with an `Observer`.

1. The `onNext` will be called 0 or more times. It will never be called more than once for the
   same event. In reactive implementations supporting backpressure, it is also important to ensure
   that the `onNext` will never be called until its previous invocation ends either synchronously
   or asynchronously. As a result, there is no need for expensive locking or synchronization of this
   method.
2. `onComplete`, `onError`, and `onError` will never be called concurrently. As a result, there is
   no need for possible expensive locking or synchronization in these calls when implementing an observer.

> **NOTE:** Your framework of choice may have rules that differ slightly from this, are more explicit in
> some situations, etc. Please refer to the documentation of the library you are using for a more complete
> listing.

## Creating an Observable

On the other side is an `Observable` which is basically just an interface that allows for an `Observer`
to subscribe to the data source. The exact details of this differ a bit between platforms. When using
scala and monix this is accomplished by using the factory methods available on the `Observable` object.

```scala
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

val source = Observable("Alpha", "Beta", "Gamme", "Delta", "Epsilon")

source
   .foreachL(name => println(name))
   .runSyncUnsafe()

```

See: [Monix Documentation](https://monix.io/docs/3x/reactive/observable.html#building-an-observable)

## Higher-Order Operators

In _most_ cases the details of these low level `Observer` and `Observable` contracts are transparent to
code authors. Why? Because a _huge_ amount of higher-order operations are available for operating on
`Observable` streams of data without having ever manually write an `Observer`. You've already seen one
example above when using the `foreachL` operator in Scala.

Let's take an example use case to try this out. Let's build an application that accepts requests from a
client as stream of text lines where each line is a "command". With that, let's implement the following
commands:

1. "echo" the received line of text to simulate a trivial command to process.
2. "sleep" for 10 seconds and then emit the string "awake" to simulate a long-running command
   to be processed.

In each case the commands should be executed in-order. If a command cannot be processed within 1
second (e.g. because the 'sleep' command takes to long) then it should immediately echo the line
"try again".

In the following example we implement this simple "app" in scala by taking advantage of a few operators.

* The `mapEval` operator executes a given [`Task`](https://monix.io/docs/3x/eval/task.html) for each element
  of the source observable.
* Dealing with the timeout condition is handled by `Task` itself which times out any command processing 
  after 1 second, cancels the execution of that task, and returns instead a message for the user indicating
  that they should try again.

```scala
import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}
import monix.reactive.Observable
import scala.concurrent.duration._

object CommandProcessor extends TaskApp {
  private val processingFinishedMsg: String =
    "No more commands to process. Shutting down!"
  private val processingTimeoutMsg: String =
    "Your command could not be processed. Please try again later."
  private val processingTimeout: FiniteDuration = 1.second
  private val sleepTime: FiniteDuration = 10.second

  def processCommand(cmd: String): Task[String] =
    if(cmd.startsWith("echo")) {
      Task.pure(cmd)
    } else if(cmd.startsWith("sleep")) {
      Task.delay("awake").delayExecution(sleepTime)
    } else {
      Task.pure(s"An unknown command $cmd was received.")
    }

  def processCommands(commands: Observable[String]): Observable[String] =
    commands
      .mapEval { cmd =>
        processCommand(cmd).timeoutTo(processingTimeout, Task.pure(processingTimeoutMsg))
      }
      .append(processingFinishedMsg)

  override def run(args: List[String]): Task[ExitCode] = {
    val commands = Observable("echo before", "sleep", "echo after")
    processCommands(commands)
      .foreachL(println)
      .map(_ => ExitCode.Success)
  }
}
```

When we put all of this together we get a system which provides the following output when given the commands:

* echo before
* sleep
* echo after

```
echo before
Your command could not be processed. Please try again later.
echo after
No more commands to process. Shutting down!
```

