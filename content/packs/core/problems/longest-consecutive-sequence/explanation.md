## The insight

Put everything in a set, then ask of each number: *am I the start of a run?* You
are the start exactly when `value - 1` is absent. If it is present, you are in the
middle of somebody else's run and counting from here would only repeat work.

From each true start, walk upwards while the next number exists.

```python
def longest_consecutive(nums):
    present = set(nums)
    best = 0
    for value in present:
        if value - 1 in present:
            continue
        length = 1
        while value + length in present:
            length += 1
        best = max(best, length)
    return best
```

## Why this is O(n), not O(n^2)

The `while` loop looks nested, and nested loops usually multiply. They do not
here: the inner walk only ever runs from the *start* of a run, and each number is
visited by exactly one such walk — the one belonging to its own run. Across the
whole function the inner loop performs at most `n` steps in total. Membership
tests are O(1) expected, so the whole thing is O(n).

Drop the `value - 1` guard and that argument collapses. On `[1, 2, 3, ..., n]`
every element would start its own walk and you would do quadratic work for the
same answer.

## Pitfalls

**Iterating `nums` instead of the set.** Correct, but duplicates make you redo
identical walks. Iterate the set.

**Sorting.** A perfectly good O(n log n) answer, and worth writing first if the
set trick will not come. Remember to skip equal neighbours rather than treating
them as a step of 1.

**The empty list.** `best` starts at 0 and the loop never runs, so it falls out
correctly — but only if you initialise to 0 rather than 1.

## Cost

O(n) expected time, O(n) space for the set.
