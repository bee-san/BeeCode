## The insight

Hold one candidate and a tally. A matching value votes for it; a differing value votes against.
When the tally hits zero, adopt the next value as the new candidate.

```python
candidate, tally = None, 0
for value in values:
    if tally == 0:
        candidate, tally = value, 1
    elif value == candidate:
        tally += 1
    else:
        tally -= 1
return candidate
```

## Why it works

Think of it as cancellation: each differing value pairs off against one copy of the candidate and
both are discarded. Every discard removes exactly one majority element at most, and one
non-majority element at least. Since the majority holds more than half the list, it has more
copies than everything else combined, so it cannot be fully cancelled — whatever survives to the
end is it.

## Where the strictness is load-bearing

"More than half" is what makes the surviving candidate the answer. With only a *plurality* — say
`[1, 1, 2, 2, 3]`, where `1` is tied for most common — the cancellation can leave `3` standing,
and the algorithm returns a wrong answer with no indication anything went amiss. That is why the
statement promises a strict majority rather than "the most frequent".

If you cannot assume it, run a second pass to count the candidate's occurrences and check it
really exceeds `len(values) // 2`. That is still O(1) space.

## Why a counter is fine too

`Counter(values).most_common(1)` is one line and O(n) time. The O(1)-space version is what the
follow-up asks for, and the cancellation argument is the transferable part — it generalises to
finding all values occurring more than `n/k` times, with `k-1` candidates instead of one.

## Pitfalls

**Resetting the tally without changing the candidate.** The adopt-and-set must happen together.

**Returning as soon as the tally passes some threshold.** The candidate may still be replaced
later; only the final survivor is guaranteed.

**A single-element list.** That element is the majority.

**Assuming the majority sits in one block.** It can be scattered, and neither method cares.

## Cost

O(n) time, O(1) space.
