package examples

import monix.execution.{Ack, Cancelable, Scheduler}
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
