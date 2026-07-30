## The insight

Let `reachable[i]` mean "the first `i` characters can be split". Position `i` is reachable
if some earlier reachable position `j` has `text[j:i]` in the dictionary:

```python
reachable[0] = True                     # the empty prefix
for end in range(1, len(text) + 1):
    for start in range(end):
        if reachable[start] and text[start:end] in allowed:
            reachable[end] = True
            break
return reachable[len(text)]
```

`reachable[0] = True` is the base case that makes everything work: the empty prefix is
trivially splittable, and it is what lets the first real word be found.

## Why greedy fails

A locally valid choice can strand the remainder. With `dictionary = ["car", "ca", "rs"]` and
`text = "cars"`, the longest matching prefix is `"car"`, which leaves `"s"` — not a word. The
shorter `"ca"` leaves `"rs"`, which is. So longest-first is wrong; and shortest-first is
equally wrong on inputs built the other way round.

Because the answer at each position depends on positions after it, every split point has to
be considered. That is precisely the situation dynamic programming exists for.

## The dictionary must be a set

`word in list` is O(len(dictionary)); `word in set` is O(1) expected. With 1000 words and
300 characters the difference is real, and converting once outside the loops is free.

## The `break`

Once a position is reachable, *how* it was reached does not matter, so stop looking. It does
not change the asymptotics but it cuts the constant substantially.

## Bounding the inner loop

If the longest dictionary word is `m` characters, only `start >= end - m` can possibly
match, so the inner loop can start there instead of at `0`. That turns O(n^2) substring
tests into O(n * m). A trie over the dictionary is the other standard refinement, matching
forward from each position without slicing at all.

## Pitfalls

**`reachable[0] = False`.** Nothing is ever reachable.

**Returning `any(reachable)`.** Any prefix being splittable is not the question; the whole
string must be.

**Greedy longest match.** As above.

**Plain recursion.** Exponential on inputs like `"aaaa...ab"` with `["a", "aa", "aaa"]`.
Memoising the position is the same computation as the table.

## Cost

O(n^2) substring tests, each O(n) to hash in the worst case, so O(n^3) strictly — O(n * m)
with the length bound. O(n) space.
