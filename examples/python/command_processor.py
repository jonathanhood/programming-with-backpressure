import rx
import time
from rx import operators as op


def process_command(cmd):
    if cmd.startswith("echo"):
        return rx.of(cmd)
    elif cmd.startswith("sleep"):
        time.sleep(10.0)
        return rx.of("awake")
    else:
        return rx.of("Invalid command {} provided.".format(cmd))


def process_command_timeout(cmd):
    return process_command(cmd)


def process_errors(notif):
    return notif.accept(
        on_next=lambda x: x,
        on_error=lambda err: "Your command could not be processed. Please try again.",
        on_completed=lambda: "No more commands to process. Shutting down!"
    )


def process_commands(commands):
    return commands.pipe(
        op.flat_map(process_command_timeout)
    )


process_commands(rx.of("echo before", "sleep", "echo after")).subscribe(print)
