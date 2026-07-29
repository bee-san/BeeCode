## The insight

Do not think about the climb forwards. Think about the **last move**.

However you reached step `n`, your final move was either a 1-step or a 2-step. If it
was a 1-step you were standing on step `n - 1` beforehand; if it was a 2-step you were
on step `n - 2`. Those two cases are disjoint — the last move was one or the other,
never both — and together they cover every possible climb. So:

```
ways(n) = ways(n - 1) + ways(n - 2)
```

with `ways(0) = 1` (stand still) and `ways(1) = 1` (one 1-step).

That is the Fibonacci recurrence, and recognising it is the point. But recognising it
is not the same as *deriving* it: the derivation is "case-split on the last decision,
check the cases are disjoint and exhaustive, recurse on the smaller problem". That
move is what solves dynamic-programming problems in general, and this Problem is the
smallest place to practise it.

## Bottom-up, two variables

You never need more than the last two answers, so you never need an array.

```python
def climb_stairs(n):
    ways_two_below, ways_one_below = 1, 1
    for _ in range(n):
        ways_two_below, ways_one_below = ways_one_below, ways_one_below + ways_two_below
    return ways_two_below
```

After `k` iterations `ways_two_below` holds `ways(k)`, so after `n` iterations it holds
the answer. The loop body is a single simultaneous assignment, which is why the
right-hand side is evaluated before either name is rebound — writing it as two separate
statements would clobber `ways_two_below` before it was used.

Three ways this goes wrong:

**Plain recursion.** `return climb_stairs(n - 1) + climb_stairs(n - 2)` is a direct
transcription of the recurrence and is exponential: `climb_stairs(40)` recomputes
`climb_stairs(10)` millions of times. At `n = 45` it is roughly 3.6 billion calls, which
is why that case is in the test suite with a time limit. The fix is either
`@functools.lru_cache` on the recursive version or the bottom-up loop above; the loop
also avoids Python's recursion-depth limit.

**Off-by-one in the base cases.** It is tempting to set the pair to `1, 2`, thinking of
`ways(1)` and `ways(2)`. That shifts the whole sequence and returns `ways(n + 1)`. Pick
your two base cases, state which index each variable represents, and check the loop
against `n = 0` and `n = 1` before anything larger — those are the only inputs where the
off-by-one is visible without arithmetic.

**Counting sets instead of sequences.** If you reason about "how many 2-steps do I
use?" you count multisets of moves and get a much smaller number. The statement says
order matters, so `1 + 2` and `2 + 1` are two climbs, and the recurrence above counts
them separately because it branches on *which* move came last.

## Cost

O(n) time, O(1) space.

Memoised recursion is also O(n) time but O(n) space for the cache plus O(n) stack
frames. A tabulated array is O(n) space. The two-variable form is strictly better than
both, and it falls out of noticing that the recurrence only ever reaches back two
steps.
