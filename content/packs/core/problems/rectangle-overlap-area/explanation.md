## The insight

An axis-aligned rectangle is just two independent ranges: one on `x`, one on `y`.
The overlap of two rectangles is therefore the overlap of their `x` ranges times
the overlap of their `y` ranges — the axes never interact.

That reduces the whole problem to a one-dimensional question asked twice: how much
do `[p1, p2]` and `[q1, q2]` share? The shared span starts at the later of the two
starts and ends at the earlier of the two ends:

```
overlap = min(p2, q2) - max(p1, q1)
```

When the ranges miss each other this comes out **negative**, and that is the piece
that needs handling rather than trusting.

## The whole solution

```python
def overlap_area(a, b):
    ax1, ay1, ax2, ay2 = a
    bx1, by1, bx2, by2 = b

    width = min(ax2, bx2) - max(ax1, bx1)
    height = min(ay2, by2) - max(ay1, by1)

    if width <= 0 or height <= 0:
        return 0
    return width * height
```

The guard is the entire problem:

**Two negatives multiply to a positive.** Rectangles that miss diagonally give a
negative width *and* a negative height, so `width * height` reports a cheerful
positive area for shapes that share nothing. `max(0, width) * max(0, height)` is
the compact fix; the explicit check says why.

**Touching is not overlapping.** An extent of exactly `0` — a shared edge or corner
— must give `0`, which `<= 0` handles and `< 0` does not. This is the difference
between "do they intersect?" and "what area do they share?", and it is why the
comparison is not strict.

## Cost

O(1) time and space: four comparisons, two subtractions, one multiplication.

The same `min` of ends minus `max` of starts is the core of interval merging,
meeting-room scheduling, and range intersection generally — it is worth
recognising in one dimension so you can apply it in two.
