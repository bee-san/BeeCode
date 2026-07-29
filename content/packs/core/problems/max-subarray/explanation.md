## The insight

The naive approach tries every subarray: pick a start, pick an end, sum it. That is
O(n^2) at best, and it recomputes sums it has already seen.

The escape is a change of question. Instead of asking "what is the best subarray?",
ask **"what is the best subarray *ending at index i*?"** That question has a
one-line answer, because a subarray ending at `i` either includes `i-1`'s subarray or
it does not:

```
best_ending_at(i) = max(nums[i], best_ending_at(i - 1) + nums[i])
```

If the previous run's total is negative, dragging it along makes things worse, so
start fresh at `nums[i]`. Otherwise extend. That is the entire decision, and it needs
only the previous value — hence O(1) space.

The answer is then the largest of these per-index bests, tracked as you go.

## The loop

```python
def max_subarray_sum(nums):
    best = current = nums[0]
    for value in nums[1:]:
        current = max(value, current + value)
        best = max(best, current)
    return best
```

Two variables, one pass. `current` is "best ending here", `best` is "best anywhere".

## The trap

Nearly everyone writes this instead:

```python
current = 0
for value in nums:
    current = max(0, current + value)   # <- wrong
    best = max(best, current)
```

Clamping at `0` says "an empty run is always an option". For `[-3, -1, -2]` that
returns `0`, but no non-empty subarray sums to 0 — the real answer is `-1`, the
largest single element. The statement requires a non-empty subarray, so the empty one
must never be a candidate.

Seeding with `nums[0]` and iterating from index 1 fixes it structurally rather than
with a special case: `current` and `best` always describe a subarray that genuinely
exists.

## Why this Problem is worth repeating

It is the smallest honest example of dynamic programming. There is no table and no
recursion, but the move is the DP move: define the answer for a prefix in terms of the
answer for the shorter prefix, then notice you only need the last value. Recognising
that reframing is what transfers to harder Problems; memorising the four lines does
not.
