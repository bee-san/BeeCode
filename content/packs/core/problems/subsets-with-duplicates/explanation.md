## The insight

Every node of the recursion tree is a subset, so record on entry rather than at a base
case:

```python
def build(start):
    found.append(list(chosen))
    for index in range(start, len(ordered)):
        if index > start and ordered[index] == ordered[index - 1]:
            continue
        chosen.append(ordered[index])
        build(index + 1)
        chosen.pop()
```

That is [Subsets](subsets) exactly, with two additions: sort the input, and skip a
value equal to the previous one *at the same level*.

## Why the skip is at the level, not globally

`index > start` restricts the rule to sibling branches. Choosing the second `2` where
the first was available produces an identical subset, so that branch is pruned. But
descending into the second `2` after having taken the first is a different subset —
`[2, 2]` — and that path runs through `index == start` on the next level, where the
rule does not fire.

This is the identical condition to the one in
[Combinations Without Reuse](combination-sum-no-reuse). Once seen, it transfers to
every "distinct combinations of a multiset" question.

## Counting the answer

`[1, 2, 2]` yields 6 subsets, not `2^3 = 8`. In general, a value occurring `c` times
contributes `c + 1` choices — take none, one, ... all `c` — so the count is the product
of `(c + 1)` over the distinct values: `2 * 3 = 6` here. Useful as a check on a
generator you are unsure about.

## Pitfalls

**Deduplicating with a set of tuples.** Correct, and it does the full `2^n` work first.
Fine to mention, then show the pruning.

**Not sorting.** The skip compares adjacent elements, so equal values must be adjacent.
`[2, 1, 2]` unsorted breaks it.

**Appending `chosen` itself.** Every recorded subset ends up being the same list, empty
at the end.

**Recording only at the leaves.** Misses every subset that is not full length.

## Cost

O(n log n) to sort, then O(number of distinct subsets * n) to produce them.
