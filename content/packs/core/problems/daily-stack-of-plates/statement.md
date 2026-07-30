Design a stack of integers supporting four operations, each in O(1) time:

- `push`, given one value, adds it to the top.
- `pop`, given nothing, removes the top value and returns it.
- `sum`, given nothing, returns the total of every value currently held.
- `size`, given nothing, returns how many values are held.

`pop` on an empty stack returns `null`, and `sum` of an empty stack is `0`.

BeeCode passes test arguments as JSON, so the operations arrive as a replay: a list where each
entry is `[name]` or `[name, value]`. Return a list holding one result per `pop`, `sum`, and `size`
operation, in order, and nothing for `push`. That is an honest simplification, not a disguise — the
replay is the same sequence of calls an object would receive.

## Constraints

- `1 <= number of operations <= 10000`
- `-10^6 <= value <= 10^6`

## Follow-up

Adding up the values on demand makes `sum` O(n). Keeping a running total makes it O(1), and the
only question is keeping that total honest across `pop`. Compare with
[A Stack That Knows Its Minimum](min-stack) — why can a running total be repaired on `pop` when a
running minimum cannot?
