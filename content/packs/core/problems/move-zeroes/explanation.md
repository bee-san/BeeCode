## The insight

Two indices. `read` scans everything; `write` marks where the next non-zero value belongs. On
finding a non-zero, swap it into the write slot and advance `write`.

```python
write = 0
for read in range(len(values)):
    if values[read] != 0:
        values[write], values[read] = values[read], values[write]
        write += 1
return values
```

## The invariant

Everything before `write` is non-zero and in original order; everything from `write` up to `read`
is zero. That holds at the start (both ranges empty) and each step preserves it: a zero at `read`
extends the zero block, and a non-zero swaps with the first zero, moving that zero to the position
`read` just vacated.

When the scan ends, `read` is past the end, so the zeroes fill the tail. Stability comes free —
non-zero values are written in the order they are read.

## Why swap rather than overwrite

`values[write] = values[read]` alone is a write, not a swap, and it duplicates values instead of
displacing zeroes: `[0, 1]` becomes `[1, 1]`. You would then have to zero the range from `write` to
the end in a second pass. That works, and is a perfectly good solution — two passes, and one
fewer temporary.

## Minimising writes

When `write == read` the swap exchanges an element with itself. Guarding with `if write != read`
skips it, so a list with no zeroes performs zero writes instead of `n` pointless ones. Irrelevant
in Python, and it matters on hardware where a write costs far more than a read, or where the
memory is flash with a limited erase budget.

## Pitfalls

**Advancing `write` on every iteration.** It advances only when something is written to it.

**Sorting the list.** Puts the zeroes first, or destroys the relative order of the rest.

**Removing and appending.** `values.remove(0)` is O(n) per call, so O(n^2) overall, and the
statement's stability requirement is easy to break while doing it.

**All zeroes or no zeroes.** Both are handled — one never swaps, the other always swaps in place.

## Cost

O(n) time, O(1) space.
