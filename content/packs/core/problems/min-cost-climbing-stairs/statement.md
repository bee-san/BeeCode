`cost[i]` is what it costs to step off stair `i`. From a stair you may climb one or two
stairs.

You may start from stair `0` or stair `1`. Return the least total cost to climb past the
top stair.

## Constraints

- `2 <= len(cost) <= 1000`
- `0 <= cost[i] <= 999`

## Follow-up

Work out the cheapest way to *reach* each stair, given that you arrive from one of the two
below it. The subtlety is the top: you are climbing past the last stair, not onto it, so
the answer is about the point one beyond the array.
