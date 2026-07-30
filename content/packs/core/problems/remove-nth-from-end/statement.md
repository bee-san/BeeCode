Remove the `n`th element counting from the end of `values` — so `n = 1` removes the
last element — and return what remains.

BeeCode passes test inputs as JSON, which cannot carry node objects, so the chain is
given as a plain list of its values rather than as `node.next` pointers. That is an
honest simplification, not a disguise: a list gives you random access a linked list
does not, so if you want the exercise the original intends, work with a cursor that
only ever moves forward.

## Constraints

- `1 <= len(values) <= 100_000`
- `1 <= n <= len(values)`
- `-10**9 <= values[i] <= 10**9`

## Follow-up

`len(values) - n` gives the index immediately, but a linked list has no length. The
classic solution makes **one** pass: start a lead pointer `n` steps ahead of a trail
pointer, then advance both until the lead reaches the end. Where is the trail
pointer then, and why?
