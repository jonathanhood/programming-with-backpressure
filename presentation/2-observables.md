# 2: Creating backpressured systems with observables

For the purposes of this talk we are going to focus on using an **observable** to
build a system which uses backpressure to respond to overload situations. This is
the solution provided as part of the reactive extensions specification and is, as
a result, available in a wide varity of languages.

## Observer and Observable

So, what is an `Observable`? At its core it's basically an interface for an `Observer`
to subscribe to a data source along with some contractual details about how that
interface will be used. In general, an `Observer` has a few important methods:

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

<details>
<summary>Python Observer Interface</summary>
<pre>
class Observer:
  def on_next(elem):
    pass1
  
  def on_complete(): 
    pass
  
  def on_error(error):
    pass
</pre>

See: [RxPy Documentation](https://rxpy.readthedocs.io/en/latest/get_started.html#get-started)
</details>

<details>
<summary>Scala Observer Interface</summary>
<pre>
trait Observer[-T] {
  def onNext(elem: T): Future[Ack]
  def onError(ex: Throwable): Unit
  def onComplete(): Unit
}
</pre>

See: [Monix Documentation](https://monix.io/docs/3x/reactive/observable.html#observable-contract)
</details>

Now, it's useful to understand just how these methods are expected to be called. To that end
there are several rules to be followed:

1. The `onComplete` will be called 0 or more times. It will never be called more than once for the
   same event. It will also never be called until its previous invocation ends either synchonrously
   or asynchronously.
2. `onComplete`, `onError`, and `onError` will never be called concurrently. As a result, there is
   no need for possible expensive locking or synchronization in these calls when implementing an observer.

On the other side is an `Observable` which is basically just an interface that allows for an `Observer`
to subscribe to the data source. The exact details of this differ a bit between platforms. For example:

* When using python and rxpy this is most commonly accomplished by using the factory operators available
  as part of the `rx` package.
* When using scala and monix this is accomplished by generating 

<details>
<summary>Creating a Python Observable</summary>
<pre>
import rx

source = rx.of("Alpha", "Beta", "Gamma", "Delta", "Epsilon")

source.subscribe(
    on_next = lambda i: print("Received {0}".format(i)),
    on_error = lambda e: print("Error Occurred: {0}".format(e)),
    on_completed = lambda: print("Done!"),
)
</pre>

See: [RxPy Documentation](https://rxpy.readthedocs.io/en/latest/get_started.html)
</details>

<details>
<summary>Creating a Scala Observable</summary>
<pre>
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

val source = Observable("Alpha", "Beta", "Gamme", "Delta", "Epsilon")

source
   .foreachL(name => println(name))
   .runSyncUnsafe()

</pre>

See: [Monix Documentation](https://monix.io/docs/3x/reactive/observable.html#building-an-observable)
</details>

Now, lets start using these new interfaces and abstractions to build a system that takes advantage of
*backpressure* in order to be more robust in the face of overload situations.
