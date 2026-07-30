## The insight

Let `best[i]` be the largest sum obtainable from the first `i` entries. For entry `i - 1`
you either take it — adding it to `best[i - 2]`, since `i - 2` is the last position it does
not conflict with — or skip it and keep `best[i - 1]`:

```text
best[i] = max(best[i - 1], best[i - 2] + values[i - 1])
```

`best[0] = 0` and `best[1] = values[0]`. Two variables suffice:

```python
skipping = taking = 0
for value in values:
    skipping, taking = taking, max(taking, skipping + value)
return taking
```

`taking` is the best over everything so far; `skipping` is the best excluding the previous
entry, which is exactly what "take this one" is allowed to build on.

## Why greedy fails

"Take every other entry" gives `1 + 3 = 4` on the first example, which happens to be right,
and `2 + 9 + 1 = 12` on the second, also right — so a small test suite can be misleading.
`[2, 1, 1, 2]` breaks it: the alternating choice is `2 + 1 = 3`, while taking the two ends
gives `4`. Similarly "take the largest, then the largest compatible" fails on
`[1, 3, 1, 3, 100]`-shaped inputs where a large value blocks two decent ones.

## The two-variable form

The swap-and-assign line is easy to write wrongly. In Python, the tuple assignment
evaluates the whole right side first, so `skipping` still holds its old value when `max`
runs. Written as two statements, the order matters and `skipping` must be updated last —
or with a temporary.

## Non-negativity earns the empty answer

Because every value is non-negative, "take nothing" only ever ties, never wins, so the
recurrence needs no separate empty case. With negative values allowed, the base cases would
have to permit choosing nothing explicitly.

## Pitfalls

**Returning `skipping`.** Off by one entry; it is the answer for the list minus its last
element.

**Building on `best[i - 1]` when taking.** That allows two adjacent entries.

**Forgetting the empty list.** `0`, straight out of the initialisation.

**A single entry.** `values[0]`, which the loop gets right on its first pass.

## Cost

O(n) time, O(1) space. The circular variant is [Houses in a Circle](house-robber-circular).
