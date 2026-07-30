## The insight

The state is a pair: how many characters of `first` and how many of `second` have been
consumed. Their sum says how much of `whole` is covered, so no third number is needed.

```text
reachable[i][j] = (reachable[i-1][j] and first[i-1]  == whole[i+j-1])
               or (reachable[i][j-1] and second[j-1] == whole[i+j-1])
```

`reachable[0][0] = True`. There are only `101 * 101` states, so what looks exponential is
tiny.

## Why greedy fails

At `first = "aa"`, `second = "ab"`, `whole = "aaba"`, the first character of `whole` could come
from either string. Commit to the wrong one and you dead-end later with no way back. Both
branches must stay alive, which is exactly what the table does.

## The length check first

`len(first) + len(second) != len(whole)` is an immediate `False`, and it is not just an
optimisation: the recurrence indexes `whole[i+j-1]`, and without the check that index can run
off the end.

## One row

Each cell needs the one above and the one to the left, so a single row updated left-to-right
suffices — the value at `other` before assignment is `reachable[i-1][j]`, and
`reachable[other - 1]` has already been updated to `reachable[i][j-1]`. Column `0` needs its
own update at the top of each row, since it has no left neighbour: it stays `True` only while
`first` matches `whole` character for character.

That column-`0` line is the one people leave out, and the failure is subtle — everything works
except inputs where one string is consumed entirely first.

## Pitfalls

**Skipping the length check.** Either a wrong answer or an index error.

**Forgetting the row-`0` initialisation.** The `second`-only prefix must be seeded before the
loop.

**Forgetting the column-`0` update inside the loop.** Breaks the `first`-only prefix.

**Counting characters instead.** An anagram check accepts `"aaab"`, which is not an
interleaving.

## Cost

O(n * m) time, O(m) space.
