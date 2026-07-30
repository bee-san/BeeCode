`meetings` is a list of `[start, end]` pairs. Return `True` if a single room can host them all,
which means no two overlap.

A meeting ending exactly when another begins is fine.

## Constraints

- `0 <= len(meetings) <= 10000`
- `meetings[i][0] <= meetings[i][1]`

## Follow-up

Once the meetings are in start order, only *adjacent* pairs can conflict — if a meeting clashes
with one two places ahead, it clashes with the one in between too. Why does that make a single
pass enough?
