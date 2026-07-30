## The insight

Two passes. First record the last index of every letter. Then scan, carrying `end`, the
furthest last-occurrence seen so far in the current part:

```python
last_seen = {character: index for index, character in enumerate(text)}
sizes, start, end = [], 0, 0
for index, character in enumerate(text):
    end = max(end, last_seen[character])
    if index == end:
        sizes.append(end - start + 1)
        start = index + 1
return sizes
```

When `index == end`, every letter in the part has been finished, so the part can close — and
closing at the first such index is what makes the number of parts maximal.

## Why the first pass is unavoidable

The decision at index `i` depends on where letters appear *later*, so a single left-to-right
pass with no lookahead cannot make it. The first pass is that lookahead, in O(n) and at most 26
entries of space.

## Why closing early is safe

If `index == end`, no letter inside the part reappears after `index`, so cutting there cannot
force a later merge. And cutting there is forced if you want the maximum number of parts —
extending further would merge two independent parts into one. Greedy is not just safe, it is
the only maximal choice.

## Interval reading

Each letter defines an interval from its first to its last occurrence, and parts are the merged
overlapping groups of those intervals. That is the same computation as merging intervals, with
the intervals never given explicitly. Seeing it that way makes the `max(end, ...)` step obvious
rather than clever.

## Pitfalls

**Recording the first occurrence instead of the last.** Cuts too early and splits letters.

**Not resetting `start`.** Every size becomes an offset from `0`.

**Closing when the current letter's last occurrence is reached.** It has to be the furthest one
across the whole part, not just this letter's.

**Returning the parts rather than their sizes.** The sizes are what is asked for; they also make
the assertion independent of slicing.

## Cost

O(n) time, O(1) space — the map holds at most 26 letters.
