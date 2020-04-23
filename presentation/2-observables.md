# 2: Creating backpressured systems with observables

For the purposes of this talk we are going to focus on using an **observable** to
build a system which uses backpressure to respond to overload situations. This is
the solution provided as part of the reactive extensions specification and is, as
a result, available in a wide varity of languages.

So, what is an `Observable`? At its core it's basically an interface for an `Observer`
to subscribe to a data source along with some contractual details about how that
interface will be used.

## Creating an Observer

In general, an `Observer` has a few important methods:

1. A method called for each data element in the `Observable` with a return from
   the method indicating when processing of that work has completed and, crucially,
   if it has complete with an error or a success. See: `on_next` (python) or `onNext` (scala).
2. A method called when the `Observable` contains no more data elements. See: `on_complete`
   (python) or `onComplete` (scala).
3. A method called when the `Observable` encountered some kind of error is encountered. See:
   `on_error` (python) or `onError` (scala).

The interface looks something like this:

### Python Observer Interface
```python
class Observer:
  def on_next(elem):
    pass
  
  def on_complete(): 
    pass
  
  def on_error(error):
    pass
```

See: [RxPy Documentation](https://rxpy.readthedocs.io/en/latest/get_started.html#get-started)

### Scala Observer Interface

```scala
trait Observer[-T] {
  def onNext(elem: T): Future[Ack]
  def onError(ex: Throwable): Unit
  def onComplete(): Unit
}
```

See: [Monix Documentation](https://monix.io/docs/3x/reactive/observable.html#observable-contract)

## The Observer Contract

Now, it's useful to understand just how these methods are expected to be called. To that end
there are several rules to be followed:

1. The `onComplete` will be called 0 or more times. It will never be called more than once for the
   same event. It will also never be called until its previous invocation ends either synchonrously
   or asynchronously.
2. `onComplete`, `onError`, and `onError` will never be called concurrently. As a result, there is
   no need for possible expensive locking or synchronization in these calls when implementing an observer.

> **NOTE:** Your framework of choice may have rules that differ slightly from this, are more explicit in
> some situations, etc. Please refer to the documentation of the library you are using for a more complete
> listing.

## Creating an Observable

On the other side is an `Observable` which is basically just an interface that allows for an `Observer`
to subscribe to the data source. The exact details of this differ a bit between platforms. For example:

* When using python and rxpy this is most commonly accomplished by using the factory operators available
  as part of the `rx` package.
* When using scala and monix this is accomplished by using the factory methods available on the `Observable`
  object.

### Creating a Python Observable

```python
import rx

source = rx.of("Alpha", "Beta", "Gamma", "Delta", "Epsilon")

source.subscribe(
    on_next = lambda i: print("Received {0}".format(i)),
    on_error = lambda e: print("Error Occurred: {0}".format(e)),
    on_completed = lambda: print("Done!"),
)
```

See: [RxPy Documentation](https://rxpy.readthedocs.io/en/latest/get_started.html)

### Creating a Scala Observable

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
2. "sleep" for 10 seconds and then emit the string "awake" to similulate a long-running command
   to be processed.

In each case the commands should be executed in-order. If a command cannot be processed within 1
second (e.g. because the 'sleep' command takes to long) then it should immediately echo the line
"try again".

### Python Command Processor

### Scala Command Processor

In the following example we implement this simple "app" in scala by taking advantage of a few operators.

* The `timeoutOnSlowDownstream` operator automatically consumes events from the source and emits them
  downstream. If the downstream processing takes less that the provided timeout then nothing happens.
  If the downstream processing takes longer than the timeout than it is
  [cancelled](https://monix.io/docs/3x/reactive/observable.html#execution) and a timeout error is
  emitted instead.
* The `materialize` operator allows us to get visibility into the errors generated by the previous stages.
  This allows us to print user-friendly messages later in the pipeline and avoid seeing a stack trace dumb
  for expected situations.
* The `mapEval` operator executes a given [`Task`](https://monix.io/docs/3x/eval/task.html) for each element
  of the source observable.

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

