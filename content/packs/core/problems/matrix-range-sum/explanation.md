## The insight

In one dimension, a running total answers any range with one subtraction:
`sum(i..j) = prefix[j + 1] - prefix[i]`. The same idea works in two dimensions,
but the bookkeeping needs care.

Define `totals[r][c]` as the sum of the whole rectangle from the top-left corner
down to — but not including — row `r` and column `c`. Every query is then
expressible in terms of four such corner rectangles.

## Inclusion–exclusion, both ways

**Building the table.** Adding the rectangle above and the rectangle to the left
double-counts their shared corner, so it comes back out once:

```
totals[r+1][c+1] = matrix[r][c] + totals[r][c+1] + totals[r+1][c] - totals[r][c]
```

**Answering a query.** Take the big rectangle, remove the strip above and the strip
to the left — and because the corner where those two strips overlap has now been
removed *twice*, add it back:

```
sum = totals[row2+1][col2+1] - totals[row1][col2+1] - totals[row2+1][col1] + totals[row1][col1]
```

That final `+` is the step people miss. Subtracting the two strips alone is wrong
by exactly the overlapping corner, which is why the answer comes out too small on
any query that does not start at row 0 and column 0 — and *correct* on any query
that does. A test suite made only of full-matrix or top-left queries passes the
buggy version.

```python
def rectangle_sums(matrix, queries):
    if not matrix or not matrix[0]:
        return [0] * len(queries)

    rows, cols = len(matrix), len(matrix[0])
    totals = [[0] * (cols + 1) for _ in range(rows + 1)]
    for r in range(rows):
        for c in range(cols):
            totals[r + 1][c + 1] = (
                matrix[r][c] + totals[r][c + 1] + totals[r + 1][c] - totals[r][c]
            )

    answers = []
    for row1, col1, row2, col2 in queries:
        answers.append(
            totals[row2 + 1][col2 + 1]
            - totals[row1][col2 + 1]
            - totals[row2 + 1][col1]
            + totals[row1][col1]
        )
    return answers
```

Two more details:

**The padding row and column are not decoration.** They are what makes `row1 == 0`
need no special case: `totals[0][...]` is genuinely zero, so "the strip above the
first row" is an empty sum rather than an index error.

**Inclusive corners mean `+ 1` on the far edge.** The table's indices are
exclusive, the query's are inclusive, and mixing the two conventions is the other
common bug here.

## Cost

O(rows · cols) setup, then **O(1) per query** — four lookups and three additions,
whatever the rectangle's size.

Summing directly is O(area) per query. With 10,000 queries over a 200×200 matrix
that is up to 400 million cell reads, against 40,000 for the setup and 10,000
constant-time answers.
