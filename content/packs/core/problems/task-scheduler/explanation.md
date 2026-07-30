## The insight

The most frequent task dictates the shape of the schedule. Say it occurs `highest`
times. Those runs split the timeline into `highest - 1` gaps, and each gap must be at
least `cooldown` long, so every one of them is a block of `cooldown + 1` units — the
task itself plus its wait:

```
[A _ _][A _ _][A _ _]A          highest = 4, cooldown = 2
```

That frame is `(highest - 1) * (cooldown + 1)` units, plus one final unit for the last
run. And if several tasks tie at `highest`, each of them needs its own slot in that
final position:

```
framed = (highest - 1) * (cooldown + 1) + at_the_highest
```

Every remaining task slots into an idle space inside the frame. Nothing else is
needed, because there are always enough gaps — that is exactly what makes the most
frequent task the constraint.

## The case the formula misses

When there are many distinct tasks, the gaps fill up completely and there is no idling
at all. Then the answer is just `len(tasks)`, which can exceed `framed`:

```python
return max(framed, len(tasks))
```

Consider `["A","A","B","B","C","C","D","D"]` with `cooldown = 2`. The frame is
`1 * 3 + 4 = 7`, but there are 8 tasks and every one must run. The formula alone
returns 7, which is impossible. This is the single most common wrong answer.

## The simulation

Instead of the formula: a max-heap of remaining counts, and a queue of tasks cooling
down. Each unit, run the most frequent available task and push it onto the cooling
queue with its release time; when a task's cooldown expires, return it to the heap.
Count the units, idling when the heap is empty and the queue is not.

O(len(tasks) * log 26) rather than O(len(tasks)), and much easier to get right —
because it makes no claim needing proof. It also produces the actual schedule, which
the formula does not. Reach for it if the closed form does not come or if the
interviewer wants the ordering.

## Pitfalls

**Missing the `max` with `len(tasks)`.** See above.

**Counting only one task at the maximum.** Ties each need a trailing slot.

**`cooldown = 0`.** The frame collapses to `highest`, and `len(tasks)` correctly wins.

**Treating idle time as free.** It occupies a unit.

## Cost

O(n) time to count, O(1) space — there are at most 26 distinct tasks.
