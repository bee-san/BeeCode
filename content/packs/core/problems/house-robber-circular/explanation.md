## The insight

The only new rule is that entries `0` and `n - 1` conflict. So every valid selection falls
into at least one of two cases:

- it does not use the last entry, so the problem is linear over `values[:-1]`
- it does not use the first entry, so the problem is linear over `values[1:]`

Solve both with the linear routine and take the larger:

```python
return max(linear(values[:-1]), linear(values[1:]))
```

That is the whole solution. The reduction is the idea worth carrying away: a wrap-around
constraint between two specific positions usually splits into cases that each exclude one
of them.

## Why the two cases suffice

Nothing is missed: a selection cannot contain both ends, so it must omit at least one, and
each omission is covered. Nothing invalid is admitted either, since each subproblem is a
genuine line — the two ends of `values[:-1]` are not adjacent in the original circle.

Overlap is harmless. A selection using neither end appears in both cases and is simply
counted twice, which cannot affect a maximum.

## The single-entry case

With one entry the two slices are both empty and the answer would come out `0` instead of
`values[0]`. It needs its own line. With two entries the slices are one element each and the
maximum is correct, so only length one is special.

## Pitfalls

**Trying to patch the linear recurrence.** A "did I take the first?" flag threaded through
the loop is doable and much harder to get right than two calls.

**Slicing wrongly.** `values[:-1]` drops the last, `values[1:]` drops the first. Swapping
them still passes symmetric tests.

**Forgetting length one.** Returns `0`.

**Running the linear solver on the whole list and subtracting something.** There is no such
correction; the interaction is not local.

## Cost

O(n) time — two linear passes — and O(n) space for the slices, or O(1) if you pass index
bounds instead of copying.
