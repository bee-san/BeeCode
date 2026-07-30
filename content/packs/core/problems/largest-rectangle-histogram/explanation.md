## The insight

Every maximal rectangle is limited in height by some particular bar — the
shortest one it contains. So consider each bar in turn as *the* limiting bar, and
ask how wide a rectangle of that exact height can be. It extends left until it
meets a strictly shorter bar, and right until it meets one. There are only `n`
candidates, and the best of them is the answer.

The work is finding those two boundaries for all bars efficiently, and that is
what a stack of indices with **increasing** heights does.

```python
def largest_rectangle(heights):
    stack = []
    best = 0
    for index in range(len(heights) + 1):
        height = 0 if index == len(heights) else heights[index]
        while stack and heights[stack[-1]] >= height:
            top = stack.pop()
            left = stack[-1] + 1 if stack else 0
            best = max(best, heights[top] * (index - left))
        stack.append(index)
    return best
```

## Reading the pop

When a bar of height `h` arrives and the stack's top is taller, that top bar can
extend no further right — `index` is its right boundary, exclusive. And its left
boundary is given by whatever is now beneath it on the stack, because the stack is
increasing: everything between them was taller and has already been popped. Hence
the width `index - (stack[-1] + 1)`, or `index` itself when the stack empties,
meaning the bar reached all the way to the left edge.

## The sentinel

The loop runs to `len(heights)` inclusive with a virtual bar of height 0. Without
it, bars still on the stack at the end — an increasing tail like `[1, 2, 3]` —
never get popped and never contribute, and the answer is too small. A zero-height
sentinel is shorter than everything, so it flushes the stack through exactly the
same code path. Draining the stack in a second loop after the main one works too;
the sentinel avoids duplicating the area arithmetic.

## Pitfalls

**Zero-height bars.** They are legal and they act as walls. The `>=` in the pop
condition handles equal heights: popping on equality loses nothing, because the
equal bar still on the stack will be credited with the full width later.

**The empty histogram.** The loop runs once with the sentinel, the stack is empty,
nothing is popped, and `best` stays 0.

## Cost

O(n) time — each index is pushed once and popped once. O(n) space for the stack,
which is full when the input is increasing.
