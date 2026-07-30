## The insight

Keep a map from reachable sum to how many assignments reach it. Each value branches every
entry in two:

```python
ways = {0: 1}
for value in values:
    following = {}
    for total, count in ways.items():
        following[total + value] = following.get(total + value, 0) + count
        following[total - value] = following.get(total - value, 0) + count
    ways = following
return ways.get(target, 0)
```

The counts **accumulate** rather than overwrite: two different assignments can reach the same
running sum, and both must be carried forward. Assigning instead of adding is the main bug,
and it silently undercounts.

## Why the map beats a list

Sums range from `-1000 * 20` to `+1000 * 20`, so an array needs an offset and a size of 40001.
A dict holds only the sums actually reachable — far fewer, especially early on — and needs no
index arithmetic.

## The subset-sum reformulation

Let `P` be the entries given `+` and `N` those given `-`. Then

```text
sum(P) - sum(N) = target
sum(P) + sum(N) = total
```

so `sum(P) = (target + total) / 2`. The question becomes "how many subsets sum to that?" — a
count-the-subsets variant of [Split Into Two Equal Halves](partition-equal-subset-sum), in
O(n * total) time and O(total) space.

It comes with two guards: if `target + total` is odd, or `abs(target) > total`, the answer is
`0`. Deriving the identity is the interesting part; that it needs those guards is what makes
the map version the safer thing to write under pressure.

## Zeroes double the count

A `0` entry can take either sign to the same effect, so each zero doubles the number of
assignments. `[0], target = 0` is `2`, not `1`. Any approach that deduplicates by *resulting
sum* rather than counting assignments gets this wrong, which is what makes it a good test.

## Pitfalls

**Overwriting instead of accumulating.** Undercounts.

**Deduplicating equal values.** Positions are distinct choices; the first example depends on
it.

**Mutating `ways` while iterating it.** Build a fresh map each level.

**Forgetting that every entry needs a sign.** Skipping is not allowed.

## Cost

O(n * S) where `S` is the number of distinct reachable sums, bounded by the total.
