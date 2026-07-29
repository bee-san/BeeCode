You are climbing a staircase of `n` steps. On each move you may climb either 1 step or
2 steps.

Return the number of distinct ways to reach the top. Two ways are distinct if the
sequence of moves differs, so `1 + 2` and `2 + 1` count separately.

There is exactly one way to climb a staircase of 0 steps: take no moves at all. So
`climb_stairs(0)` is `1`.

## Constraints

- `0 <= n <= 45`
- The answer fits comfortably in a Python integer.

## Follow-up

The recursive definition is easy to write and catastrophically slow, because it
recomputes the same subproblems exponentially many times. Can you compute the answer
with a single loop and only two variables of memory?
