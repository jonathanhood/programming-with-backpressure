package examples

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
