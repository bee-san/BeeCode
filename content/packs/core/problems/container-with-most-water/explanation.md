## The insight

Begin with the two outermost walls — the widest container there is — and then
throw one away each step. Which one? The shorter.

Here is why that is safe. Say the left wall is the shorter. Any *other* container
using that left wall is narrower, and its height is still capped by that same
short wall. So every remaining pair involving it holds strictly less than the one
you just measured. It can be discarded with nothing lost.

```python
def max_water(heights):
    left, right = 0, len(heights) - 1
    best = 0
    while left < right:
        best = max(best, (right - left) * min(heights[left], heights[right]))
        if heights[left] <= heights[right]:
            left += 1
        else:
            right -= 1
    return best
```

## Pitfalls

**Discarding the taller wall.** The mirror image of the argument does not hold: a
tall wall may well be part of the best answer, paired with a different partner.
Move the short side.

**Using the max instead of the min.** The water spills over the lower wall. It is
the shorter that sets the level.

**Trying to skip ahead.** Advancing past every wall shorter than the one you just
left is a valid optimisation but not needed here, and getting it wrong loses
answers. One step at a time is O(n) already.

**Ties.** When the walls are equal it does not matter which you drop; either
choice keeps the argument intact, because both are equally the shorter.

## Cost

O(n) time and O(1) space. The pointers only ever move towards each other, so
together they take at most `n` steps.
