Cars drive along a one-lane road towards a target at position `target`. Car `i`
starts at `positions[i]` and travels at `speeds[i]`. All positions are distinct
and every car is before the target.

A faster car that catches up to a slower one cannot overtake. It joins that car
and the two travel together at the slower speed, as a **fleet**. A fleet that
catches another fleet merges into it. Cars that meet exactly at the target count
as having met.

Return how many distinct fleets arrive at the target.

## Constraints

- `0 <= len(positions) == len(speeds) <= 100_000`
- `0 < positions[i] < target <= 10**6`, all positions distinct
- `0 < speeds[i] <= 10**6`

## Follow-up

Work in units of time, not distance. Consider the cars in order from the one
nearest the target, backwards. When does a car behind you *not* join your fleet?
