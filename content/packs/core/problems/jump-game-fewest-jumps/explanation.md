## The insight

Think of it as breadth-first search on a line, but without building a queue. Three numbers:

- `taken` — jumps used so far
- `current_end` — the last index reachable with `taken` jumps
- `furthest` — the last index reachable with `taken + 1` jumps

Sweep left to right, extending `furthest` at every index. When `index` reaches `current_end`,
the current level is exhausted: spend a jump and let the next level run to `furthest`.

```python
taken = current_end = furthest = 0
for index in range(len(jumps) - 1):
    furthest = max(furthest, index + jumps[index])
    if index == current_end:
        taken += 1
        current_end = furthest
return taken
```

## Why the loop stops one short

`range(len(jumps) - 1)` excludes the last index. Arriving there means you are done; letting the
loop visit it would spend one extra jump when `current_end` happens to land exactly on it. This
off-by-one is the single most common bug here, and it only shows up on some inputs — which is
why `[2, 3, 1, 1, 4]` and `[1, 1]` are both worth testing.

## Why this is a level sweep, not a per-index choice

It is tempting to jump to whichever index has the largest `jumps` value. That is a different
greedy and it is wrong, because a nearer index with a smaller value can still reach further
overall — what matters is `index + jumps[index]`, not `jumps[index]`. The sweep compares
exactly that quantity, across the whole level, before committing.

The DP alternative — `best[i] = 1 + min(best[j])` over all `j` that reach `i` — is O(n^2) and
gives the same answer. The sweep is the same computation with the levels found implicitly.

## Pitfalls

**Looping to the last index.** Overcounts by one on some inputs.

**Choosing the largest `jumps[i]` rather than the largest `i + jumps[i]`.** Wrong greedy.

**A single element.** `0` jumps; the loop body never runs.

**Updating `current_end` before extending `furthest`.** The level boundary must be the furthest
reach across the *whole* level, so extend first, then check.

## Cost

O(n) time, O(1) space.
