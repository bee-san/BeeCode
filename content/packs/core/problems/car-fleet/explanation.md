## The insight

Stop tracking where the cars are. Ask only **when** each one would reach the
target if the road were empty:

```
arrival = (target - position) / speed
```

Now sort the cars from nearest the target to furthest, and walk backwards down the
road. Keep `slowest_ahead`, the arrival time of the fleet currently in front.

- `arrival <= slowest_ahead` — this car is at least as fast as the fleet ahead, so
  it catches up somewhere on or before the target. It joins, and the fleet's
  arrival time is unchanged, because the fleet moves at the slower speed.
- `arrival > slowest_ahead` — it is slower and never catches up. It starts a new
  fleet, and becomes the thing the cars behind it must catch.

```python
def count_fleets(target, positions, speeds):
    fleets = 0
    slowest_ahead = 0.0
    for position, speed in sorted(zip(positions, speeds), reverse=True):
        arrival = (target - position) / speed
        if arrival > slowest_ahead:
            fleets += 1
            slowest_ahead = arrival
    return fleets
```

## Where the stack went

This is usually presented as a monotonic stack: push arrival times, and pop
whenever the new car's time is not greater than the top. But the popped values are
never read again — only the count and the current maximum matter — so the stack
collapses to one variable. Recognising when a stack is really an accumulator is
worth as much as knowing the stack pattern.

## Pitfalls

**Sorting the wrong way.** Front to back, i.e. descending by position. Ascending
gives a plausible-looking answer that is wrong on the first example.

**Strict versus non-strict.** Meeting exactly at the target counts as merging, so
a new fleet needs `arrival > slowest_ahead`. Using `>=` counts such a pair twice.

**Integer division.** `(target - position) // speed` truncates and makes distinct
arrival times compare equal, merging fleets that never meet. Use real division —
or compare the fractions cross-multiplied, `(target - p1) * s2` against
`(target - p2) * s1`, to avoid floating point entirely.

## Cost

O(n log n) for the sort, then one linear pass. O(n) space for the sorted copy.
