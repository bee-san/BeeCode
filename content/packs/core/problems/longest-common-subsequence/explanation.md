## The insight

Let `best[i][j]` be the answer for the first `i` characters of `first` and the first `j` of
`second`:

```text
if first[i-1] == second[j-1]:  best[i][j] = best[i-1][j-1] + 1
else:                          best[i][j] = max(best[i-1][j], best[i][j-1])
```

**Matching characters.** Both are consumed together, so the subproblem shrinks diagonally.
Taking a matching pair is always safe — there is never a reason to skip a match — which is
why this case needs no `max`.

**Non-matching characters.** At least one of the two must be discarded, and which one is not
knowable locally, so try both and take the better. This is where the exponential recursion
comes from, and where the table earns its place: there are only `(n+1) * (m+1)` distinct
subproblems.

Row and column `0` are `0`: nothing is common with an empty string.

## Two rows instead of a table

Each row depends only on the one above and on values already written in the current row, so
two rows suffice — O(min(n, m)) space if you make the shorter string the inner dimension.
The full table is only needed to *reconstruct* the subsequence, by walking backwards from
`best[n][m]` and following whichever choice produced each value.

## Subsequence, not substring

Skipping is allowed, so `"ace"` counts inside `"abcde"`. The contiguous version is a
different and simpler recurrence — a match extends the run, a mismatch resets it to `0`, and
the answer is the maximum over the whole table rather than its final cell.

## Pitfalls

**Off-by-one between table and string indices.** The table is `1`-based so that row `0` can
mean "empty prefix"; the string index is therefore `i - 1`. Mixing them is the standard bug.

**Using `max` in the matching case.** Harmless, since taking the match always wins — but it
signals a misunderstanding.

**Overwriting the row in place.** `best[i-1][j]` would be lost. Two rows, or iterate
carefully with a saved diagonal value.

**Returning the maximum over the table.** That is the substring version's answer.

## Cost

O(n * m) time, O(min(n, m)) space.
