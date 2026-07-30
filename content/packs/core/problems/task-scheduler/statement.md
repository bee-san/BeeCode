`tasks` is a list of task names. Each takes one time unit, and in each unit the
processor either runs one task or idles.

Two runs of the **same** task must be separated by at least `cooldown` units of
anything else — other tasks or idle time.

Return the least total time needed to run every task.

## Constraints

- `1 <= len(tasks) <= 10_000`
- Task names are single uppercase letters `A`-`Z`
- `0 <= cooldown <= 100`

## Follow-up

The task with the highest count is what forces the schedule: it lays down a frame of
slots that everything else fills. That gives a closed-form answer — but there is a
case where the frame is irrelevant and the answer is simply `len(tasks)`. Find it,
because the formula alone gets it wrong.
