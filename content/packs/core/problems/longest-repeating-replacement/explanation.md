## The insight

Pick the window first and the letter second. A window of length `L` whose most
common character occurs `m` times can be turned into a run of `L` identical
characters by changing the other `L - m`. So the window is legal exactly when

```
L - m <= k
```

Grow the window on the right, and whenever it becomes illegal, shrink from the
left. The answer is the longest legal window ever seen.

```python
def longest_run(s, k):
    counts = {}
    left = 0
    best = 0
    for right, character in enumerate(s):
        counts[character] = counts.get(character, 0) + 1
        while (right - left + 1) - max(counts.values()) > k:
            counts[s[left]] -= 1
            left += 1
        best = max(best, right - left + 1)
    return best
```

You never decide *which* letter to standardise on. The count of the most common
character is all the formula needs.

## The famous shortcut

Many published solutions never decrease `most_common`, treating it as a
high-water mark rather than the true maximum. That still returns the right answer,
which surprises people. The reason: a stale, too-large `most_common` only ever
makes the window condition too permissive, so the window can be too long — but a
window is only *recorded* as the best when it is at least as long as the one that
set that high-water mark, and such a window would have been legal anyway. The
final maximum is therefore unaffected.

It is a real optimisation — it drops the O(26) recount — but it is a poor thing to
lead with in an interview, because the invariant it maintains is not the one the
code appears to state. Recomputing `max(counts.values())` costs a constant 26 and
means what it says.

## Pitfalls

**`k = 0`.** Then the answer is the longest existing run of one character. The
formula handles it; a solution that assumes at least one change does not.

**Shrinking with `if` instead of `while`.** One removal is not always enough to
restore legality after the recount.

**Trying each of the 26 letters separately.** Legitimate — 26 independent
two-pointer passes — but 26 times the work for the same answer.

## Cost

O(26n) = O(n) time, O(26) = O(1) space.
