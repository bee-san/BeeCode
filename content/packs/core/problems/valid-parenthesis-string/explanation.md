## The insight

Track the **range** of possible open-bracket counts: `lowest` if every star so far were as
closing as possible, `highest` if every star were an opening bracket.

```python
lowest = highest = 0
for character in text:
    if character == "(":
        lowest, highest = lowest + 1, highest + 1
    elif character == ")":
        lowest, highest = lowest - 1, highest - 1
    else:
        lowest, highest = lowest - 1, highest + 1
    if highest < 0:
        return False
    lowest = max(lowest, 0)
return lowest == 0
```

Two guards, and each is doing something different.

**`highest < 0` is fatal.** Even treating every star as `(`, there are more closers than
openers — no choice can recover.

**`lowest < 0` clamps to `0`.** A negative low means some choices have over-closed, but those
choices are simply invalid; the achievable counts still include `0`. Letting `lowest` go
negative would then require a matching over-count later and reject valid strings — `"(*)"`
among them.

## Why the range is enough

Every count between `lowest` and `highest` is achievable: adjusting one star's meaning changes
the count by one, so the reachable set is a contiguous interval with no holes. That is what
lets two numbers stand in for the whole set of possibilities, and it is the reason this is O(1)
space instead of a DP table over (index, count).

At the end, balanced means a count of `0` is reachable — and since `lowest` is clamped at `0`
and `lowest <= highest` throughout, `lowest == 0` is exactly that test.

## Pitfalls

**One counter.** Cannot represent the ambiguity.

**Not clamping `lowest`.** Rejects valid strings.

**Returning `highest == 0`.** Wrong direction: `highest` being positive is fine, since stars
counted as `(` can be reinterpreted as nothing.

**Checking the guards before updating.** The guards apply to the counts *after* consuming the
character.

**An empty string.** `True`.

## Cost

O(n) time, O(1) space.
