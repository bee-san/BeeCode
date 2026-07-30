## The insight

Work upwards. Start with the bottom row as the answer for "cheapest descent from here", then fold
each row above into it:

```python
best = list(rows[-1])
for row in reversed(rows[:-1]):
    for position in range(len(row)):
        best[position] = row[position] + min(best[position], best[position + 1])
return best[0]
```

`best[0]` is then the cheapest descent from the apex.

## Why bottom-up has no edge cases

Going down, entry `j` of row `i` reaches entries `j` and `j + 1` below — and both always exist,
because the row below is one longer. So the recurrence applies uniformly to every entry with no
boundary check.

Going top-down, entry `j` is reachable from entries `j - 1` and `j` above, and at the two edges one
of those does not exist. That is two special cases per row, both easy to write and both avoidable by
reversing the direction.

Choosing the direction in which the recurrence has no boundaries is a general move worth having, and
this problem is the cleanest illustration of it.

## Why one row suffices

Row `i` depends only on row `i + 1`, and the sweep writes `best[position]` after reading
`best[position]` and `best[position + 1]` — both still holding row `i + 1`, since positions are
written in increasing order and only `position + 1` is read ahead. So an in-place update is safe.

Note the contrast with [The Cheapest Way Down and Right](min-path-sum-grid), where the in-place
update deliberately reads the *already-overwritten* neighbour. Which one is correct depends entirely
on the direction of dependence, and neither is a rule to memorise.

## Negative entries

Entries may be negative, so no early exit based on "the total can only grow" is available, and every
path must be considered. The recurrence never assumes otherwise, which is why it needs no change —
but it is worth checking rather than assuming.

## Pitfalls

**Greedily choosing the smaller of the two children.** Locally cheapest, globally wrong: a small
step can lead into a costly subtree.

**Copying the bottom row by reference.** `best = rows[-1]` would mutate the caller's input.

**Working top-down without handling the edges.** Two special cases per row.

**A one-row triangle.** The answer is its single entry, and the loop does not run.

## Cost

O(n^2) time in the number of rows, O(n) space.
