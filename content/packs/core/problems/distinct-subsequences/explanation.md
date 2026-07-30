## The insight

Let `counts[i][j]` be the number of subsequences of the first `i` characters of `text` that
equal the first `j` of `pattern`.

```text
counts[i][j] = counts[i-1][j]                            # skip text[i-1]
             + counts[i-1][j-1]  if text[i-1] == pattern[j-1]   # also use it
```

The `+` is the whole Problem. When the characters match you do **not** choose between using
and skipping — both give valid subsequences at different positions, and the answers add. Take
a `max` instead and you have written
[Longest Common Subsequence](longest-common-subsequence): same table, one operator different,
completely different question.

`counts[i][0] = 1` for every `i`: the empty pattern is matched by exactly one subsequence, the
empty one. `counts[0][j] = 0` for `j > 0`: an empty text spells nothing.

## Collapsing to one row, backwards

Keeping only `counts[j]` and walking `j` **downwards**:

```python
counts = [0] * (len(pattern) + 1)
counts[0] = 1
for character in text:
    for index in range(len(pattern), 0, -1):
        if pattern[index - 1] == character:
            counts[index] += counts[index - 1]
return counts[len(pattern)]
```

`counts[index - 1]` must still hold the value from *before* this character was processed.
Ascending would let one `text` character satisfy two positions of `pattern` at once — the same
descending-loop argument as [Split Into Two Equal Halves](partition-equal-subset-sum), and
here `text = "aa"`, `pattern = "aa"` catches it: ascending gives 3, the answer is 1.

## Why positions, not characters

`"rabbbit"` contains three `b` characters and the pattern needs two, so there are three ways
to choose which pair is used. Deduplicating by resulting *string* would give 1 and miss the
question entirely.

## Pitfalls

**`max` instead of `+`.** Answers a different Problem.

**Ascending inner loop.** Reuses a character within one step.

**`counts[0] = 0`.** Everything is zero.

**Skipping the non-matching case in the 2D form.** `counts[i][j] = counts[i-1][j]` must be
copied even when the characters differ. The collapsed form gets this for free, which is part
of why it is easier to write correctly.

## Cost

O(len(text) * len(pattern)) time, O(len(pattern)) space.
