Reorder `values` so that it alternates between taking from the front and from the
back: first element, last element, second element, second-to-last, and so on.

`[1, 2, 3, 4]` becomes `[1, 4, 2, 3]`, and `[1, 2, 3, 4, 5]` becomes
`[1, 5, 2, 4, 3]`.

BeeCode passes test inputs as JSON, which cannot carry node objects, so the chain is
given as a plain list of its values rather than as `node.next` pointers. That is an
honest simplification, not a disguise: a list gives you random access a linked list
does not, so if you want the exercise the original intends, work with a cursor that
only ever moves forward.

## Constraints

- `0 <= len(values) <= 100_000`
- `-10**9 <= values[i] <= 10**9`

## Follow-up

The classic linked-list version cannot index from the back. It does this instead:
find the middle with a slow and a fast pointer, reverse the second half, then weave
the two halves together. Try it that way — the answer is the same and the technique
is the point.
