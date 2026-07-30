## The insight

Do not look for surrounded regions. Look for the safe ones, which are easy to find
because you know where they start:

1. From every `"O"` on the border, flood inwards and mark each reached cell `"S"`.
2. Sweep the whole board: every remaining `"O"` was unreachable from the border, so flip
   it to `"X"`; every `"S"` becomes `"O"` again.

```python
for column in range(columns):
    keep(0, column); keep(rows - 1, column)
for row in range(rows):
    keep(row, 0); keep(row, columns - 1)
```

The alternative — flood each region, remember its cells, check whether any of them sits
on the border, flip if not — is more bookkeeping and more places to be wrong.

## The third marker

`"S"` is what makes the two-pass structure work. Without a distinct marker you cannot
tell, in the final sweep, a safe `"O"` from a doomed one. It has to be a value that
cannot already appear on the board, and it has to be cleaned up before returning — the
board must come back holding only `"X"` and `"O"`.

## Small boards are all border

Any board with fewer than three rows or three columns has no interior at all, so nothing
is ever captured. This falls out of the algorithm for free — every cell is a seed — which
is a good sign the shape is right. Written the other way it is a special case to
remember.

## Pitfalls

**Marking on pop rather than on push.** The same cell enters the stack repeatedly.

**Forgetting to restore `"S"`.** The board comes back with a marker in it.

**Seeding only the corners, or only two edges.** Every border cell is a potential seed.

**Recursing without a depth guard on a 50x50 board of `"O"`.** 2500 deep; use the
explicit stack.

## Cost

O(rows * columns) time — each cell is marked at most once and swept once — and O(1)
extra space beyond the stack, since the board itself carries the marks.
