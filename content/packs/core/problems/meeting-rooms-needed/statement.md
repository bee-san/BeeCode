`meetings` is a list of `[start, end]` pairs. Return the fewest rooms needed to host them all.

A meeting ending exactly when another begins may reuse the same room.

## Constraints

- `0 <= len(meetings) <= 100000`
- `meetings[i][0] <= meetings[i][1]`

## Follow-up

The answer is the largest number of meetings running at the same instant. You never need to know
*which* meeting is in which room — only how many are in progress. What if you separated the
starts from the ends and swept both in time order?
