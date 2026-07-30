`stones` holds the weights of a pile of stones. Repeat this until at most one stone
remains: take the two heaviest, `x <= y`, and smash them together.

- if `x == y` both are destroyed
- otherwise the heavier one is replaced by a stone of weight `y - x`

Return the weight of the stone that is left, or `0` if none is.

## Constraints

- `1 <= len(stones) <= 30`
- `1 <= stones[i] <= 1000`

## Follow-up

The process always asks for the two largest, and each smash puts a new value back into
the pile. Sorting once is not enough because the replacement has to be reinserted in
order — which structure gives you "largest out, arbitrary value in" cheaply?
