package examples

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
