package examples

import monix.eval.Coeval
import monix.execution.Scheduler.Implicits.global
import monix.reactive.{Observable, OverflowStrategy}
import org.scalatest.wordspec.AnyWordSpec

class TestCommandProcessor extends AnyWordSpec {
  "A CommandProcessor" should {
    "process some example commands" in {
      val commands = Observable("echo before", "sleep", "echo after")
      val result = CommandProcessor.processCommands(commands).toListL.runSyncUnsafe()
      assert(result == List(
        "echo before",
        "Your command could not be processed. Please try again later.",
        "echo after",
        "No more commands to process. Shutting down!"
      ))
    }

    "buffer and deal with a source that can't be backpressured" in {
      val overflowStrategy      = OverflowStrategy.DropNewAndSignal(8, dropped => Coeval.delay(Some(s"echo dropped ${dropped}")))
      val commandSource         = new RudeCommandSource(List("echo before", "sleep", "echo after"), overflowStrategy)
      val result                = CommandProcessor.processCommands(commandSource).take(10).toListL.runSyncUnsafe()
      CommandProcessor.processCommands(commandSource)
        .take(10)
        .foreachL(println)
        .runSyncUnsafe()
    }
  }
}
