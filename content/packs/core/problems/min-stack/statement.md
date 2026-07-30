Design a stack that also reports its smallest element, with **every** operation
running in O(1) — including the minimum.

Because BeeCode tests functions rather than classes, you are given the operations
as a list and must replay them. Each operation is a two-element list:

- `["push", value]` — push `value`
- `["pop", null]` — remove the top element
- `["top", null]` — report the top element
- `["min", null]` — report the smallest element currently in the stack

Return a list holding the result of each `top` and `min`, in order. `push` and
`pop` produce nothing. `pop` and `top` and `min` are never called on an empty
stack.

## Constraints

- `1 <= len(operations) <= 20_000`
- `-10**9 <= value <= 10**9`
- Values may repeat.

## Follow-up

Scanning for the minimum is O(n). Keeping one "current minimum" variable is O(1)
but breaks the moment that element is popped — you have no idea what the minimum
was before it arrived. What if each entry remembered that for you?
