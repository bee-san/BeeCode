## The insight

Build `lengths[i]` = the longest border of the prefix ending at `i`. Each entry follows from the
previous one:

```python
lengths = [0] * len(text)
for index in range(1, len(text)):
    candidate = lengths[index - 1]
    while candidate > 0 and text[index] != text[candidate]:
        candidate = lengths[candidate - 1]
    if text[index] == text[candidate]:
        candidate += 1
    lengths[index] = candidate
return lengths[-1]
```

The answer is the last entry.

## The fallback is the whole idea

Suppose the prefix ending at `index - 1` has a border of length `k`. If `text[index]` equals
`text[k]`, the border extends to `k + 1`. If not, the next candidate is not `k - 1` — it is
`lengths[k - 1]`, the longest border **of that border**.

Why: any shorter border of the prefix ending at `index - 1` must also be a border of its length-`k`
border. So the borders form a chain, and following `lengths[k - 1]` walks it in decreasing order
without skipping any candidate. Decrementing by one instead would test lengths that cannot possibly
be borders, and would be wrong as well as slower.

## Why the whole thing is O(n)

`candidate` rises by at most 1 per iteration of the outer loop, so it can fall by at most `n` in
total across all iterations of the inner loop. That is amortised O(1) per position — the same
accounting as a sliding window.

## What this is for

This table *is* the preprocessing step of Knuth-Morris-Pratt substring search, and it also answers
"what is the shortest string whose repetition gives `text`" — `n - lengths[n-1]` is the period, when
it divides `n`. Worth knowing as more than a puzzle.

## Pitfalls

**Falling back to `candidate - 1`.** Tests impossible lengths and misses real borders.

**Allowing the whole string as its own border.** It must be proper; `lengths[i]` counts borders of
the prefix ending at `i`, which is why the loop starts at index 1 and `lengths[0]` is 0.

**Comparing `text[index]` against `text[index - candidate]`.** The comparison is against
`text[candidate]`, the next character of the prefix.

**A single character.** No proper non-empty prefix exists, so `0`.

## Cost

O(n) time, O(n) space.
