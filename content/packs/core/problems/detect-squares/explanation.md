## The insight

Store a count per coordinate pair, since duplicates are distinct points.

For a `count` query at `(x, y)`, iterate over every stored point `(a, b)` as the **diagonally
opposite** corner. It qualifies only if

- `a != x` and `b != y` — otherwise the square has zero area, and
- `abs(a - x) == abs(b - y)` — the diagonal of an axis-aligned square is at 45 degrees.

The remaining two corners are then forced: `(x, b)` and `(a, y)`. Multiply the three counts:

```python
total = 0
for (a, b), quantity in counts.items():
    if a == x or b == y:
        continue
    if abs(a - x) != abs(b - y):
        continue
    total += quantity * counts.get((x, b), 0) * counts.get((a, y), 0)
```

## Why the diagonal and not an adjacent corner

Picking an adjacent corner leaves the square's orientation open — it could extend up or down —
so you would count each square twice and need to halve, or track direction. The diagonal pins it
completely: two opposite corners of an axis-aligned square determine the other two uniquely.
Each square is counted exactly once, with no correction factor.

## Why multiply the counts

Duplicate points are separate, so if the corner `(x, b)` was added three times, there are three
distinct squares differing only in which copy is used. The count of squares is the product across
the three other corners — the standard multiplication principle, and the reason a set of points
would give the wrong answer.

## Why iterating the map beats iterating the offsets

`count` is O(number of distinct stored points), because each one is tried once as the opposite
corner and the other two corners are then two dictionary lookups. The alternative — trying every
possible side length from 1 to 1000 — is O(coordinate range), which is fixed work regardless of
how little data there is. Proportional to the data is the better bound here, since the range is
1000 and the point count may be far smaller.

## Pitfalls

**Using a set instead of a counter.** Undercounts whenever a point is added twice.

**Omitting the `a != x` and `b != y` checks.** Counts degenerate zero-area "squares".

**Comparing signed differences.** `a - x == b - y` catches only one of the two diagonal
directions.

**Iterating over adjacent corners.** Double-counts.

**Emitting a result for `add`.** The output holds one entry per `count` only.

## Cost

O(1) per add, O(d) per count with `d` distinct points, O(d) space.
