# Using Back-pressure

* [Home](https://jonathanhood.github.io/programming-with-backpressure/)
* [Prev](observables.md)

Up until now we haven't really taken advantage of backpressure to do anything useful. In our previous
example we were able to rationally apply a timeout to a command, but this did nothing to backpressure
the source and slow down the rate of incoming commands. Let's learn some strategies for dealing with
overload situations and apply them to our example.

## Words are hard

Before we get started, let's get some terminology out of the way. You see, there are few synonyms that
are used interchangeably to indicate _direction_ of data flow that can be confusing at first:

* The `Observable` represents the "source", "upstream", or "producer". It is the thing that _sends_
  messages and can be back-pressured.
* The `Observer` represents the "sink", "downstream", or "consumer". It is the thing that _receives_
  messages and can deliver back-pressure.
  
To add to this confusion there is an intermediate concept - that is a type that is _both_ and `Observable`
and an `Observer`. May of the common library-supplied operators (e.g. `map`, `flatMap`, `filter`, etc) operate
this way. As a result, they can both _deliver_ back-pressure upstream and _respond to_ back-pressure downstream.

## How is back-pressure signaled?

An `Observer` provides back-pressure though the return value of `onNext`. It can signal a few things back to
the `Observable` that are really important.

1. Is the computation performed by the `Observer` synchronous or asynchronous?
2. Did the computation performed by the `Observer` result in some kind of error?
3. Did the computation performed by the `Observer` indicate that the stream should continue or stop?

All of these elements are _crucial_ to a fully formed idea of back-pressure because they allow for some
important and significant use cases to be implemented in an `Observer`. Some examples that may be relevant
to systems you work with:

1. An operator (that is both an `Observer` and an `Observable`) may want to decouple its downstream
   and upstream paths by means of buffering and asynchronous processing.
2. An `Observer` may represent some kind of network hop, IPC boundary, database query, or other action
   that is fundamentally asynchronous in nature.
3. An `Observer` may do error checking or input validation and desire to cancel the stream _early_ to
   avoid continuing to computing bad data.
4. An `Observable` may represent a source that _cannot_ be back-pressured and, therefore, buffering
   (along with some kind of management strategy such as tail drop) is _required_ to avoid data loss.
   
In each of these cases our `onNext` call can return different values to signify different conditions
in the dowstream `Observer`.

1. If an uncompleted `Future` is returned then the `Observable` must asynchronously await the result.
2. If the returned `Future` is `Cancelable` then it also indicates that the downstream computation can
   be asynchronously shut down before it emits a result.
3. An `Ack.Continue` result indicates that processing should continue as normal.
4. An `Ack.Stop` result indicates that downstream is no longer interested in receiving events.

In addition to these cases the returned `Future` may also supply an exception indicating that an error
unhandled has occurred downstream.

## Building a Basic Operator that Back Pressures

So, that's a lot to take in. It turns out, though, that it's pretty straightforward to implement some code using
these semantics. Let's consider the `map` operator. Such an operator would have a few requirements.

1. For a given "map" predicate function (e.g. `x * 2`) it should apply that transformation to every element
   received from the operator's upstream and send it to the operator's downstream.
2. When the operator's downstream back-pressures it should transparently provide, again, that back-pressure
   to the operator's upstream.
3. When the operator's upstream signals an error it should transparently provide, again, that error
   to the operator's downstream.
4. When the operator's upstream signals completion it should transparently provide, again, that error
   to the operator's downstream.

It turns out that this isn't such a difficult thing to build and use.

```scala
import monix.execution.{Ack, Cancelable}
import monix.reactive.{Observable, Observer}
import monix.reactive.observers.Subscriber
import scala.concurrent.Future

final class MapOperator[A,B](upstream: Observable[A], mapFunc: A => B) extends Observable[B] {
  override def unsafeSubscribeFn(downstream: Subscriber[B]): Cancelable =
    upstream.subscribe(new FlatMapObserver(downstream))(downstream.scheduler)

  private class FlatMapObserver(downstream: Subscriber[B]) extends Observer[A] {
    override def onNext(elem: A): Future[Ack] =
      downstream.onNext(mapFunc(elem))

    override def onError(ex: Throwable): Unit =
      downstream.onError(ex)

    override def onComplete(): Unit =
      downstream.onComplete()
  }
}
```

Of course, under normal circumstances you would never reinvent this particular wheel. You would, instead,
use the built-in `map` functionality.

```scala
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

Observable(1,2,3)
  .map(_*2)
  .toListL
  .runSyncUnsafe()

// List(3,4,6)
```

## Dealing with a Rude Data Source

Now, lets take on the use case of a data source that just _keeps sending data_ even when my system
becomes  overloaded. That's what we are all here for right? To deal with this we will employ a few
strategies.

1. We will buffer the source up to a fixed size.
2. When the buffer fills we will start dropping the newest messages (tail drop) and signal downstream
   that this has happened.

To kick this off, let's build a particularly rude data source for the `CommandProcessor` we build
previously. To be clear, the goal here is _not_ provide an example of what to do but, perhaps, what
_not_ do to and/or how to deal with sources that act in this manner.

Our `RudeCommandSource` uses a tight loop continuously send commands forward without respect to backpressure.
There _are_ analogies to this in the real world:

1. An external system requesting data at a high rate.
2. Incoming packets that simply cannot be handled with the CPU power / bandwidth /etc we have available.
3. "Push-based" Messaging and IPC protocols that don't have a good, reliable, or easy to use concept of
   back-pressure (e.g. JMS and many other brokered "queueing" systems) themselves.
   
Also completely _normal_ data sources can begin to _look_ like this when dowstream systems experience issues.
Perhaps the database is under an unusual amount of load from another source, or perhaps a disk has died and your
backing RAID array is rebuilding. The possibilities are endless.

```scala
import monix.eval.Task
import monix.execution.{Ack, Cancelable}
import monix.execution.ChannelType.SingleProducer
import monix.execution.atomic.AtomicBoolean
import monix.reactive.{Observable, OverflowStrategy}
import monix.reactive.observers.{BufferedSubscriber, Subscriber}
import scala.util.{Failure, Success}

final class RudeCommandSource(commandsRepeated: List[String], overflowStrategy: OverflowStrategy[String])
  extends Observable[String]
{
  override def unsafeSubscribeFn(subscriber: Subscriber[String]): Cancelable = {
    // A "Subscriber" is just an "Observer" with some extra magic attached
    val buffered = BufferedSubscriber(subscriber, overflowStrategy, SingleProducer)
    sendCommands(buffered, AtomicBoolean(false)).runToFuture(buffered.scheduler)
  }

  // This is _not_ the kind of code you would write in the real world. It's just a hack
  // to build a particularly rude command source.
  private def sendCommands(subscriber: Subscriber[String], stopped: AtomicBoolean): Task[Unit] =
    Task.deferAction { implicit sched =>
      commandsRepeated.foreach { cmd =>
        if(!stopped.get) {
          subscriber.onNext(cmd).onComplete {
            case Success(Ack.Stop) | Failure(_) => stopped.set(true)
            case Success(Ack.Continue) => ()
          }
        }
      }
      if(!stopped.get) {
        sendCommands(subscriber, stopped)
      } else {
        Task.unit
      }
    }
}
```

Let's use our fancy new `RudeCommandSource` in an example and talk about what happens.

```scala
import monix.eval.Coeval
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Observable, OverflowStrategy}
import org.scalatest.wordspec.AnyWordSpec
import examples.{CommandProcessor, RudeCommandSource}

val overflowStrategy      = OverflowStrategy.DropNewAndSignal(8, dropped => Coeval.delay(Some(s"echo dropped ${dropped}")))
val commandSource         = new RudeCommandSource(List("echo before", "sleep", "echo after"), overflowStrategy)

CommandProcessor.processCommands(commandSource)
  .take(10)
  .foreachL(println)
  .runSyncUnsafe()
```

Something like the following eventually gets printed to the console:

```
echo before
echo dropped 87
echo dropped 456
echo dropped 166
echo dropped 211
echo dropped 255
echo dropped 112
echo dropped 95
echo dropped 295
echo dropped 110
```

From that output it's _clear_ that we dropped quite a few events. After all, the second command issued was "sleep"
which shuts down the system for a full second while it processes. Mean while our source is just pounding away with
new commands. Let's dive a bit deeper into each step to se what happened though.

1. We buffered the source with a `BufferedSubscriber`.
2. We used an `OverflowStrategy` to manage the buffer and set the buffer size to 8. We signalled downstream the
   number of events dropped to the user by an "echo" command downstream.
3. Since the source is infinitely sized (it will just continue spewing until we shut it down) we used a `take(10)`
   to make it finite in nature.
4. We then printed the results of the stream using `println`.

So, for not a _ton_ of effort we now have a system that rationally handles overload situations. Success! Don't
stop here, though, there are a _ton_ of alternative strategies that can be employed. The
[Monix Documentation](https://monix.io/docs/3x/reactive/observable.html#back-pressure-buffering-throttling) contains
some great information about strategies you can employ in your applications - even if you aren't a Scala developer.
