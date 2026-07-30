## The insight

Sort the intervals by start, and process the queries in increasing order — but remember each
query's original position so the answers can be written back where they belong.

Sweep with a min-heap keyed by interval size:

- **Add** every interval whose start is at or before the current query. Once added, an interval
  stays a candidate until it expires.
- **Discard** from the top any interval whose end is before the query. The heap is ordered by
  size, so the top is the shortest — if the shortest has expired, drop it and look again.
- **Read** the top: its size is the answer, or `-1` if the heap is empty.

```python
order = sorted(range(len(queries)), key=lambda i: queries[i])
answers = [-1] * len(queries)
available, index = [], 0
for position in order:
    value = queries[position]
    while index < len(ordered) and ordered[index][0] <= value:
        start, end = ordered[index]
        heapq.heappush(available, (end - start + 1, end))
        index += 1
    while available and available[0][1] < value:
        heapq.heappop(available)
    if available:
        answers[position] = available[0][0]
```

## Why sorting the indices, not the queries

The answers must come back in the original query order, and sorting the values alone destroys
it. Sorting the *positions* by their value gives the sweep order while keeping each answer's
destination — `answers[position]` writes straight to the right slot, with no second pass to
undo a permutation.

## Why expired intervals can be dropped from the top only

The heap is keyed by size, not by end, so an expired interval can sit anywhere in it. Dropping
only from the top looks unsound — but it is fine: queries increase, so anything expired now stays
expired forever, and it will be discarded whenever it eventually reaches the top. It can never
be returned as an answer in the meantime, because to be returned it would have to *be* the top,
and the discard loop runs first. Each interval is pushed once and popped at most once.

## Why increasing query order matters

It makes both loops monotone: `index` never goes back, and an expired interval never revives. A
single interval is touched O(log n) times in total rather than once per query, which is what
turns O(len(intervals) * len(queries)) into O((n + q) log n).

## Inclusive sizes

`end - start + 1`, so `[4, 4]` has size `1`. Computing `end - start` gives `0` there and shifts
every answer by one.

## Pitfalls

**Sorting the queries in place.** Loses the output order.

**Keying the heap by end.** Then the top is not the shortest.

**Discarding by scanning the whole heap.** O(n) per query and unnecessary.

**Forgetting the `+ 1`.** Off by one everywhere.

**Popping before pushing.** Newly added intervals must be present before the expiry check, or a
valid short interval can be missed.

## Cost

O((n + q) log n) time, O(n + q) space.
