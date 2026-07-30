Given a list `values` of non-negative integers, choose a subset with the largest possible
sum, subject to one rule: **you may not choose two adjacent entries**.

Return that largest sum. Choosing nothing is allowed, so the answer is never negative.

## Constraints

- `0 <= len(values) <= 100`
- `0 <= values[i] <= 400`

## Follow-up

At each entry there are exactly two possibilities: take it, which rules out its neighbour,
or skip it. Both branches reduce to the same question on a shorter list, and the recursion
has only two states worth remembering. Which two?
