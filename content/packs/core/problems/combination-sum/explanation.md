## The insight

Deduplicating afterwards is the wrong shape. Make the duplicates unreachable by
**never going backwards**: at each level, only consider candidates from the current
index onwards.

```python
def build(start, remaining):
    if remaining == 0:
        found.append(list(chosen))
        return
    for index in range(start, len(ordered)):
        value = ordered[index]
        if value > remaining:
            break
        chosen.append(value)
        build(index, remaining - value)     # index, not index + 1: reuse allowed
        chosen.pop()
```

That single constraint means every combination is generated in non-decreasing order,
and each multiset therefore appears exactly once. `[2, 3]` is reachable; `[3, 2]` is
not, because once you have taken index 1 you can never look at index 0 again.

Passing `index` rather than `index + 1` is what allows reuse — the same candidate may
be taken again, but nothing earlier can. Pass `index + 1` and you get
[the no-reuse variant](combination-sum-no-reuse).

## Sorting, and the pruning it buys

Sorting `candidates` first is not required for correctness — the index rule alone
prevents duplicates — but it makes each combination come out ascending, and it enables
the `break`. Once a candidate exceeds what remains, every later one does too, so the
whole rest of the loop can be abandoned rather than tested one by one. On a wide
candidate set that is most of the work.

## Mark, recurse, undo

`chosen.append` / recurse / `chosen.pop` is the backtracking skeleton. Two details:
record `list(chosen)` and not `chosen` itself, or every recorded answer is the same
list object, empty by the time it is returned; and the `pop` must happen on every path
out of the loop body.

## Pitfalls

**Passing `index + 1`.** Silently answers the no-reuse problem.

**Passing `0`.** Generates every ordering, so `[2, 2, 3]`, `[2, 3, 2]` and `[3, 2, 2]`
all appear.

**Recursing when `remaining < 0`.** Harmless with the `break` in place, but without
sorting it wastes an entire subtree per overshoot. Check before descending.

**Positive candidates are essential.** A zero or negative candidate makes the
recursion unbounded, which is why the constraints exclude them.

## Cost

Exponential in the worst case — there can be exponentially many combinations, and they
all have to be produced. The pruning is what keeps it tractable.
