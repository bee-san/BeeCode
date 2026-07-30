`jumps[i]` is the furthest you may move forward from index `i`. You start at index `0`, and the
last index is guaranteed reachable.

Return the fewest jumps needed to get there.

## Constraints

- `1 <= len(jumps) <= 10000`
- `0 <= jumps[i] <= 1000`
- The last index is always reachable.

## Follow-up

[Can You Reach the End](jump-game) needed one number; this needs three, and they describe a
level-by-level sweep. Which indices are exactly `k` jumps from the start, and what tells you
when one level ends and the next begins?
