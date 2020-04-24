package examples

import monix.reactive.Observable
import monix.execution.Scheduler.Implicits.global

object CreateObservable {
  val source = Observable("Alpha", "Beta", "Gamme", "Delta", "Epsilon")

  source
    .foreachL(name => println(name))
    .runSyncUnsafe()
}
