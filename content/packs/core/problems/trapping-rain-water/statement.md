`heights` describes a terrain of unit-wide bars, where `heights[i]` is the height
of the bar at position `i`. Rain falls and settles. Return how many units of
water the terrain holds.

Water at position `i` rises to the level of the lower of the two tallest bars on
either side of it, and whatever is below the bar itself is not water. So the water
above position `i` is

    max(0, min(tallest to the left, tallest to the right) - heights[i])

Water at the outer edges runs off.

## Constraints

- `0 <= len(heights) <= 100_000`
- `0 <= heights[i] <= 100_000`
- Return `0` for an empty terrain.

## Follow-up

Precomputing the two "tallest so far" arrays gives an O(n) time, O(n) space
solution and is the clearest way to see the formula. Then note that a pair of
pointers walking inwards already knows one of the two maxima for certain — which
one, and why is that enough?
