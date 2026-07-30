## The insight

Keep the hit timestamps in arrival order — which is sorted, since they arrive non-decreasing — and
maintain a head index marking the oldest one still in the window.

```python
while head < len(hits) and hits[head] <= timestamp - 300:
    head += 1
```

Then `count` is `len(hits) - head`. Discarding is done on both operations, so the window is always
current when it is read.

## Why the boundary is `<=` and not `<`

The window is `(timestamp - 300, timestamp]`: a hit exactly 300 seconds ago is *out*. A hit at second
1 is still counted at second 300 (a span of 300 seconds inclusive) and gone at second 301. Getting
this wrong is off by one in a way that no amount of ordinary testing catches, which is why the
statement writes the interval out and the tests probe both sides of it.

## Amortised cost

Each hit is discarded at most once, so the total work in the `while` loop across all operations is
O(number of hits). Any single `count` may be slow — if 5000 hits expire at once, it discards all
5000 — but the average is O(1). Amortised is the honest claim; worst-case-per-call is not.

## The dense-hit alternative

Two arrays of 300 slots: one holding a second's timestamp, one holding its count. A hit at time `t`
writes to slot `t % 300`, resetting that slot's count first if its stored timestamp is stale. `count`
sums the slots whose timestamps are in the window.

That is O(300) per `count` and O(1) space regardless of how many hits arrive — better when hits are
dense, worse when they are sparse. The per-slot *timestamp* is the part that makes it work: without
it there is no way to tell a slot holding this window's hits from one holding a previous window's.

## Pitfalls

**Discarding only on `count`.** Works, and lets the list grow without bound between counts.

**Using `<` for the boundary.** Includes a hit exactly 300 seconds old.

**Removing from the front of a list with `pop(0)`.** O(n) per removal; use an index or a deque.

**Assuming one hit per second.** Duplicated timestamps are explicitly allowed.

## Cost

O(1) amortised per operation, O(hits in the window) space.
