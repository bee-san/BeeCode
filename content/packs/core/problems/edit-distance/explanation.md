## The insight

Let `cost[i][j]` be the distance between the first `i` characters of `source` and the first
`j` of `target`.

```text
if source[i-1] == target[j-1]:
    cost[i][j] = cost[i-1][j-1]                     # nothing to pay
else:
    cost[i][j] = 1 + min(cost[i-1][j-1],            # replace
                         cost[i-1][j],              # delete from source
                         cost[i][j-1])              # insert into source
```

The three predecessors are the three edits, and reading which is which off the indices is the
part worth being able to do from memory:

- **replace** consumes one character from each side, so both indices drop.
- **delete** consumes a `source` character and no `target` character, so only `i` drops.
- **insert** matches a `target` character against nothing in `source`, so only `j` drops.

## The base cases carry real information

`cost[i][0] = i` — deleting every character is the only way to reach the empty string — and
`cost[0][j] = j`, inserting every character. Getting these wrong (`0` everywhere) makes the
whole table collapse to nonsense, and it is the most common cause of a distance that comes out
too small.

In the code above `previous = list(range(len(target) + 1))` *is* row `0`, and `[index] + ...`
seeds column `0` of each new row.

## Two rows

Each cell needs the one above, the one to the left, and the diagonal. Two rows cover all
three, so O(min(n, m)) space. The full table is needed only to recover the actual edit script,
by walking back from `cost[n][m]`.

## Relatives

Forbid replace and this becomes deletions and insertions only, which is
`n + m - 2 * lcs(n, m)` — see
[Longest Common Subsequence](longest-common-subsequence). Charge nothing for a match, one for
everything else, and it is the same table with different weights. The whole family is one
recurrence with different costs.

## Pitfalls

**Zeroed base cases.** Understates the answer whenever one string is a prefix of the other.

**Adding `1` in the matching branch.** Overcounts by the number of matches.

**Only two of the three predecessors.** Dropping the diagonal turns replace into
delete-then-insert, doubling its cost. `"a"` to `"b"` returns 2 instead of 1.

**In-place single row.** Loses the value above.

## Cost

O(n * m) time, O(min(n, m)) space.
