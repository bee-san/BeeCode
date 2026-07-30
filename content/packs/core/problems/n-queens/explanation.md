## The insight

Two decisions collapse the problem.

**One queen per row.** Since `n` queens must go on `n` rows without sharing one, every
row holds exactly one. So instead of choosing squares, choose a column for row 0, then
row 1, and so on. Row conflicts become structurally impossible.

**Three sets for the other constraints.** A candidate square `(row, column)` is
attacked if:

- `column` is already used, or
- `row + column` is already used — that value is constant along a `/` diagonal, or
- `row - column` is already used — constant along a `\` diagonal

Each check is O(1).

```python
def place(row):
    if row == n:
        found.append(render(placement))
        return
    for column in range(n):
        if column in columns or (row + column) in rising or (row - column) in falling:
            continue
        columns.add(column); rising.add(row + column); falling.add(row - column)
        placement.append(column)
        place(row + 1)
        placement.pop()
        columns.discard(column); rising.discard(row + column); falling.discard(row - column)
```

## Why the diagonal identities hold

Moving one step down-right increases `row` by one and `column` by one, so `row - column`
does not change — that is the `\` direction. Moving down-left increases `row` and
decreases `column`, leaving `row + column` unchanged — the `/` direction. Two squares are
on a shared diagonal exactly when one of those sums matches. Worth deriving on paper
once; it appears in every diagonal-constraint problem afterwards.

## Undoing all three

The three `discard` calls must mirror the three `add` calls exactly. Missing one leaves a
phantom attack that silently suppresses valid arrangements — the count comes out too low
with nothing looking wrong. A quick check: `n = 4` has 2 arrangements, `n = 5` has 10,
`n = 6` has 4. If the numbers drift, an undo is missing.

## Pitfalls

**No solutions for `n = 2` and `n = 3`.** An empty list is the right answer, not an
error. Both are in the suite.

**Rendering with the wrong padding.** `"." * column + "Q" + "." * (n - column - 1)`,
where the `- 1` accounts for the queen itself.

**Scanning the board to test a square.** O(n) per check instead of O(1), and much more
code to get wrong.

**Using a list instead of a set.** Membership becomes O(n). It still passes at `n <= 9`;
the sets are the point.

## Cost

Exponential — there is no polynomial algorithm — but the pruning is severe: a conflict
at row `k` eliminates every arrangement extending that prefix. Space is O(n).
