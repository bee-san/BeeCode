## The insight

Three pointers carve the list into four regions:

```
[ 0s ][ 1s ][ unexamined ][ 2s ]
      low   cursor       high
```

Everything before `low` is a settled 0, everything from `low` to `cursor - 1` is
a settled 1, everything after `high` is a settled 2. The loop shrinks the
unexamined middle to nothing.

```python
def sort_colours(nums):
    low, cursor, high = 0, 0, len(nums) - 1
    while cursor <= high:
        if nums[cursor] == 0:
            nums[low], nums[cursor] = nums[cursor], nums[low]
            low += 1
            cursor += 1
        elif nums[cursor] == 2:
            nums[high], nums[cursor] = nums[cursor], nums[high]
            high -= 1
        else:
            cursor += 1
    return nums
```

This is Dijkstra's Dutch national flag partition, and it is the same three-way
split that makes quicksort behave on inputs full of duplicates.

## Why the two swaps are not symmetric

On a 0 the cursor advances; on a 2 it does not. The asymmetry is the one thing to
understand here.

Swapping with `low` brings back something from the region already scanned, and
everything there is a 1 — already in the right place, so move on. Swapping with
`high` brings back something from the region **never examined**. It could be
another 2. Re-test the same position.

Advance the cursor on a 2 and `[2, 2]` ends as `[2, 2]` only by luck; on
`[2, 0, 2, 1, 1, 0]` it leaves a 2 stranded in the middle.

## Pitfalls

**`cursor < high`.** The final position needs examining too. Use `<=`.

**A two-way partition.** Splitting "0 versus not-0" and then "1 versus 2" is two
passes. Correct, but it is the thing this Problem is asking you to avoid.

## Cost

O(n) time and O(1) extra space, in a single pass. Each iteration either advances
`cursor` or retreats `high`, so there are at most `n` of them.
