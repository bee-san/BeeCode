## Why the ordinary sliding window fails

The two-pointer window works on non-negative inputs because the sum is *monotonic*
in the window: extending it can only help, shrinking it can only hurt. So when a
window qualifies you can safely shrink from the left.

With negatives that is false. `[84, -37, 32, 40, 95]` with `k = 167`: the window
must span the `-37`, and shrinking a qualifying window can make the sum go *up*.
There is no invariant left to maintain, so the technique does not apply.

## Restating it with prefix sums

Let `prefix[i]` be the sum of the first `i` elements. Then the sum from `i` to
`j - 1` is `prefix[j] - prefix[i]`, and the question becomes:

> For each `j`, find the **largest** `i < j` with `prefix[j] - prefix[i] >= k`.

Largest `i`, because that gives the shortest subarray ending at `j`.

## Which candidates can be discarded

Two rules, and together they are the whole solution:

**Once used, gone forever.** If `prefix[j] - prefix[i] >= k`, record the length
`j - i` and drop `i` permanently. Any later `j'` pairing with the same `i` gives a
*longer* subarray, so `i` can never help again.

**Dominated candidates are useless.** If an earlier candidate `i` has
`prefix[i] >= prefix[j]`, then `j` is both a smaller prefix (easier to reach `k`
from) and closer (shorter subarray). `i` is beaten on both counts — drop it.

The second rule is what keeps the surviving candidates in strictly increasing
order of prefix value. That is why a plain deque suffices: the best candidate is
always at the front, and additions only ever happen at the back.

```python
import collections

def shortest_subarray(nums, k):
    prefix = [0]
    for value in nums:
        prefix.append(prefix[-1] + value)

    best = len(nums) + 1
    candidates = collections.deque()
    for index, total in enumerate(prefix):
        while candidates and total - prefix[candidates[0]] >= k:
            best = min(best, index - candidates.popleft())
        while candidates and prefix[candidates[-1]] >= total:
            candidates.pop()
        candidates.append(index)

    return best if best <= len(nums) else -1
```

Details that decide correctness:

**Store indices, not sums.** The answer is a *length*, so you need `index - i`.

**`prefix` has `n + 1` entries** and the loop runs over all of them, including the
leading `0`. Skipping it loses every subarray that starts at position 0.

**Pop from the front before pushing.** The current index is a legitimate candidate
for later positions but must not be paired with itself: a zero-length subarray is
not an answer. Draining first, then pushing, makes that impossible rather than
guarded against.

## Cost

O(n) time and O(n) space. Every index is pushed once and popped at most once, so
the two inner `while` loops do amortised constant work despite being nested.

The brute force is O(n²), and it is not merely slower — at 50,000 elements it is
2.5 billion additions.
