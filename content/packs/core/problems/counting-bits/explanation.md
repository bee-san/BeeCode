## The insight

`value >> 1` is `value` with its last bit dropped. So `value` has whatever bits `value >> 1` has,
plus possibly the last one:

```python
counts = [0] * (limit + 1)
for value in range(1, limit + 1):
    counts[value] = counts[value >> 1] + (value & 1)
return counts
```

`value >> 1 < value` for every positive `value`, so the entry being read is always already
written. One pass, O(limit).

## The other recurrence

`counts[value] = counts[value & (value - 1)] + 1` also works, using the bit-clearing identity from
[How Many Bits Are Set](count-one-bits): clearing the lowest set bit gives a strictly smaller
number with exactly one fewer set bit.

Both are O(limit) and both need only that the index read is smaller than the index written. The
shift version is easier to see at a glance; the clearing version generalises better to problems
where you iterate over subsets.

## Why not just count each number

`sum(count_bits(i) for i in range(limit + 1))`-style independent counting is O(limit log limit) —
about 1.7 million operations at `limit = 100000` rather than 100000. Correct, and 17 times the
work, because it recomputes prefixes it already knows.

This is the smallest honest example of dynamic programming: the subproblem is a genuinely smaller
instance of the same question, and the table is the memo.

## The zero entry

`counts[0] = 0` and the loop starts at `1`. Starting at `0` would read `counts[0 >> 1]`, which is
`counts[0]` — the entry being written — and while that happens to give the right answer here, a
recurrence that reads the cell it writes is not one to get in the habit of.

## Pitfalls

**A list of length `limit` rather than `limit + 1`.** The range is inclusive.

**Using `value // 2` and thinking it differs.** For non-negative integers it is the same as
`value >> 1`.

**`counts[value >> 1] + value & 1`.** `+` binds tighter than `&` in Python, so this parses as
`(counts[value >> 1] + value) & 1`. Parenthesise.

**`limit = 0`.** The answer is `[0]`, and the loop runs zero times.

## Cost

O(limit) time and O(limit) space for the output.
