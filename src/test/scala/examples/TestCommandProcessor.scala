package examples

import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
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
  }
}
