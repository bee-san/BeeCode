## The insight

Two rules, one for each kind of duplication.

**No element twice** — recurse with `index + 1`, so each position is consumed at most
once.

**No duplicate combinations** — sort first, then at each level skip a value equal to
its predecessor *within the same loop*:

```python
if index > start and ordered[index] == ordered[index - 1]:
    continue
```

`index > start` is the whole subtlety. Within one loop, the first occurrence of a value
is allowed and any later occurrence at the same depth is skipped — because taking the
second `1` instead of the first, at the same position, produces an identical
combination. But `index == start` means this is the first choice at this level, which is
how `[1, 1]` stays reachable: the first `1` is taken, and the recursive call starts at
the second, where it is again the first choice.

Drop `index > start` and you lose every combination with a repeated value. Drop the
whole condition and duplicates come back.

```python
def build(start, remaining):
    if remaining == 0:
        found.append(list(chosen))
        return
    for index in range(start, len(ordered)):
        value = ordered[index]
        if value > remaining:
            break
        if index > start and value == ordered[index - 1]:
            continue
        chosen.append(value)
        build(index + 1, remaining - value)
        chosen.pop()
```

## Why sorting is mandatory here

In the reuse variant sorting only bought pruning. Here it is required: the skip rule
compares against the *immediate predecessor*, which only identifies duplicates when
equal values are adjacent.

## Pitfalls

**Deduplicating at the end.** Works — with sorted tuples in a set — and hides the
technique. It also does exponentially more work before discarding it.

**`index > 0` instead of `index > start`.** Kills `[1, 1]` and every other combination
that legitimately repeats a value.

**Passing `index`.** Reuses the element, answering the other Problem.

**Comparing against `chosen[-1]`.** Not the same test. The rule is about sibling
branches at one level, not about the value just taken.

## Cost

O(n log n) to sort, then exponential in the number of combinations produced. The
`break` on overshoot prunes heavily because the input is sorted.
