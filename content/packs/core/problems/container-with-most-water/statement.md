`heights[i]` is the height of a vertical wall standing at position `i`. Any two
walls form a container. Its width is the distance between their positions and its
height is the **shorter** of the two walls, so it holds

    (j - i) * min(heights[i], heights[j])

units of water. The walls themselves are infinitely thin and the ones in between
do not matter.

Return the most water any pair can hold.

## Constraints

- `2 <= len(heights) <= 100_000`
- `0 <= heights[i] <= 10_000`

## Follow-up

Every pair is O(n^2). Start with the widest container instead, and ask what you
can conclude about the shorter of its two walls.
