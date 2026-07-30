## The insight

Dijkstra's algorithm from `start`, then take the maximum:

```python
pending = [(0, start)]
while pending:
    elapsed, label = heappop(pending)
    if label in settled:
        continue                          # a shorter route already settled it
    settled[label] = elapsed
    for destination, weight in outgoing[label]:
        if destination not in settled:
            heappush(pending, (elapsed + weight, destination))

return -1 if len(settled) != n else max(settled.values())
```

The heap always yields the smallest unsettled distance, and because every weight is
positive, nothing can later arrive cheaper — so the first pop of a node is final.

## Minimum then maximum

Each node receives the signal at its *shortest* distance, but "everyone has it" waits for
the *furthest* of those. Reaching for a single extreme, either way, is the classic misread:
summing the distances, or taking the smallest, both have a plausible-sounding story.

## The stale-entry skip

The same node can be pushed several times, once per route discovered. `if label in
settled: continue` discards the later, longer copies. Without it, a node's distance can be
overwritten by a worse one and the maximum comes out too large. This lazy-deletion form is
simpler than a decrease-key heap and is what you write in an interview.

## Unreachability is a count

Directed links mean some nodes may be unreachable. They never enter `settled`, so
`len(settled) != n` is the check. Comparing against a sentinel "infinite" distance works
equally well; counting avoids choosing a sentinel.

## Labels start at 1

Adjacency built over `range(1, n + 1)`. A list indexed from `0` is off by one, and node
`n` then raises or is silently missed.

## Pitfalls

**Breadth-first search instead.** Finds the fewest links, not the least time. Correct only
when every weight is equal.

**Summing the distances.** That is total travel, not elapsed time — the signal propagates
in parallel.

**No stale-entry check.** Distances get overwritten with longer ones.

**Assuming links are two-way.** They are directed; the second example turns on exactly
that.

## Cost

O(e log e) time, O(n + e) space. See [Cheapest Route](cheapest-route) for the same
algorithm answering a single-destination question.
