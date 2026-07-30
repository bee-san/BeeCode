Return the first `rows` rows of Pascal's triangle.

Row `0` is `[1]`. Every later row starts and ends with `1`, and each interior entry is the sum of
the two entries above it.

## Constraints

- `1 <= rows <= 30`

## Follow-up

Each row follows from the one before, which is a two-line recurrence. There is also a closed form —
entry `k` of row `n` is the binomial coefficient `n` choose `k` — and it is worth knowing why the
recurrence is the better way to build the whole triangle even so.
