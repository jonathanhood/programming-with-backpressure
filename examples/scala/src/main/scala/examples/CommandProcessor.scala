package examples

import cats.effect.ExitCode
import monix.eval.{Task, TaskApp}
import monix.reactive.Observable
import scala.concurrent.duration._

object CommandProcessor extends TaskApp {
  private val processingFinishedMsg: String =
    "No more commands to process. Shutting down!"
  private val processingTimeoutMsg: String =
    "Your command could not be processed. Please try again later."
  private val processingTimeout: FiniteDuration = 1.second
  private val sleepTime: FiniteDuration = 10.second

  def processCommand(cmd: String): Task[String] =
    if(cmd.startsWith("echo")) {
      Task.pure(cmd)
    } else if(cmd.startsWith("sleep")) {
      Task.delay("awake").delayExecution(sleepTime)
    } else {
      Task.pure(s"An unknown command $cmd was received.")
    }

  def processCommands(commands: Observable[String]): Observable[String] =
    commands
      .mapEval { cmd =>
        processCommand(cmd).timeoutTo(processingTimeout, Task.pure(processingTimeoutMsg))
      }
      .append(processingFinishedMsg)

  override def run(args: List[String]): Task[ExitCode] = {
    val commands = Observable("echo before", "sleep", "echo after")
    processCommands(commands)
      .foreachL(println)
      .map(_ => ExitCode.Success)
  }
}
