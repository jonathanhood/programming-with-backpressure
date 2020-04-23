import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable

object CreateObservable {
  val source = Observable("Alpha", "Beta", "Gamme", "Delta", "Epsilon")

  source
    .foreachL(name => println(name))
    .runSyncUnsafe()
}
