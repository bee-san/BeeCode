## The insight

Sorting is information. Because `nums` is ascending, looking at *one* element tells
you about *all* the others: if the middle element is smaller than the target, then
everything to its left is smaller too, and the entire left half is eliminated by a
single comparison.

Each comparison halves the search space, so 100,000 elements takes 17 questions
rather than 100,000. That is the whole algorithm. The difficulty is not the idea; it
is stating the loop so precisely that it always terminates.

The discipline that makes it precise: name your **invariant** and never break it.
Here the invariant is *if `target` is in `nums` at all, its index lies in the
inclusive range `[low, high]`*. Every line below either preserves that or returns.

## The loop

```python
def search(nums, target):
    low, high = 0, len(nums) - 1
    while low <= high:
        middle = (low + high) // 2
        if nums[middle] == target:
            return middle
        if nums[middle] < target:
            low = middle + 1
        else:
            high = middle - 1
    return -1
```

Read it against the invariant. `high` starts at `len(nums) - 1` because the range is
inclusive on both ends. The loop condition is `low <= high` because a range where
`low == high` still contains one candidate and must be examined. And when
`nums[middle]` is not the target, `middle` itself is excluded — hence `middle + 1` and
`middle - 1`, never plain `middle`.

Three errors, each with a distinct symptom:

**`while low < high`.** Skips the final one-element window. `search([5], 5)` returns
`-1`: `low` and `high` are both 0, the loop never runs. Wrong answer, silently.

**`low = middle` or `high = middle`.** This is the infinite loop. If `low` is 0 and
`high` is 1, then `middle` is 0; assigning `low = middle` leaves the window exactly as
it was and the loop spins forever. Excluding `middle` is what guarantees the window
shrinks on every iteration, and a shrinking window is what guarantees termination.

**`high = len(nums)`.** Now the range is half-open but the loop condition still treats
it as inclusive, so the first probe can index one past the end. Pick inclusive or
half-open and make the initialisation, the condition, and the updates all agree —
mixing the two conventions is the source of most binary-search bugs.

The empty list needs no special case: `high` is `-1`, `low <= high` is false
immediately, and you return `-1`.

One more note for production code: `(low + high) // 2` is safe in Python because
integers are arbitrary precision. In a fixed-width language that sum can overflow, and
the standard fix is `low + (high - low) // 2`. Worth knowing that the idiom exists and
why.

## Cost

O(log n) time, O(1) space. The iterative form uses no stack; a recursive version is
equally correct but costs O(log n) frames.
