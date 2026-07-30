## The insight

Two structures, each doing one job.

A hash map from key to that key's history gives O(1) access to the right timeline.
The timeline itself is an append-only pair of parallel lists — timestamps and
values — and because sets arrive in increasing timestamp order it is **already
sorted**. No sorting, no insertion in the middle.

Then `get` is a binary search for the rightmost timestamp that does not exceed `t`:

```python
low, high, best = 0, len(times) - 1, -1
while low <= high:
    middle = (low + high) // 2
    if times[middle] <= timestamp:
        best = middle           # a candidate; try for a later one
        low = middle + 1
    else:
        high = middle - 1
```

That `best` variable is what makes this an *upper-bound* search rather than an
exact-match search. Every time the midpoint is admissible, record it and keep
looking right; the last thing recorded is the answer.

## Pitfalls

**Scanning the timeline.** A key written 20,000 times and read 20,000 times makes an
O(n) `get` quadratic overall.

**Returning `""` for "not found" versus "no value yet".** An unknown key and a key
whose first write is later than `t` both yield `""`, and both paths need covering —
the second is the one that gets forgotten.

**`bisect` off by one.** `bisect_right(times, t) - 1` is the same search in one
line, and the `- 1` is essential; `bisect_left` answers a different question when
`t` is present exactly.

**Storing a dict per key keyed by timestamp.** Exact lookups become O(1) and the
"largest not exceeding" query becomes O(number of writes), which is the query that
actually matters.

## Cost

`set` is O(1). `get` is O(log w) where `w` is the number of writes for that key.
Space is O(total writes).
