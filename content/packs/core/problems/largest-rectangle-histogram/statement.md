`heights` gives the heights of adjacent unit-wide bars in a histogram. Return the
area of the largest rectangle that fits entirely inside it.

The rectangle must be axis-aligned and span a contiguous range of bars; its height
is limited by the shortest bar in that range. So for `[2, 1, 5, 6, 2, 3]` the
answer is `10` — the bars of height 5 and 6 give a 2-by-5 rectangle.

## Constraints

- `0 <= len(heights) <= 100_000`
- `0 <= heights[i] <= 10_000`
- Return `0` for an empty histogram.

## Follow-up

For each bar, ask how far the rectangle *of exactly that height* can extend left
and right before it meets something shorter. Then the answer is the best of those
`n` candidates — and a stack computes all the boundaries in one pass.
