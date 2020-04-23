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

> **NOTE:** To simplify this tutorial we will continue forward with the Scala camel-case naming
> for these methods.

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
* When using scala and monix this is accomplished by generating 

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

Now, lets start using these new interfaces and abstractions to build a system that takes advantage of
*backpressure* in order to be more robust in the face of overload situations.
