## The insight

A right rotation by `k` moves the last `k` elements to the front, order preserved. Three reversals
achieve that:

1. Reverse the whole list. The last `k` elements are now at the front, but backwards, and so is
   the rest.
2. Reverse the first `k`. They are now in their original relative order, at the front.
3. Reverse the remaining `n - k`. Likewise.

```python
flip(0, n - 1)
flip(0, k - 1)
flip(k, n - 1)
```

Each element is touched at most twice, so it is O(n) time with O(1) extra space.

## Reducing `steps` first

`steps % len(values)` is not an optimisation, it is a correctness requirement: with `steps` up to
`10^9` the boundaries of steps 2 and 3 would be nonsense. And `steps` equal to the length reduces
to `0`, where step 2 becomes `flip(0, -1)` — a no-op, since `low < high` is false immediately, so
no special case is needed.

## The cycle-following alternative

You can also walk the permutation directly: move `values[i]` to `values[(i + k) % n]`, follow where
that displaces, and keep going until you return to the start. The number of cycles is
`gcd(n, k)`, so you need an outer loop over that many starting points — a genuinely nice piece of
number theory, and much easier to get wrong than three reversals.

## Why in place

The statement asks for it, and returning the same object makes the mutation observable through the
JSON test protocol. `values[:] = values[-k:] + values[:-k]` mutates the right object but allocates
two temporary lists, so it is O(n) space wearing an in-place disguise.

## Pitfalls

**Not taking `steps` modulo the length.** Out-of-range slice boundaries or a wasted `10^9`
iterations.

**Rotating left.** Reversing the pieces in the other order gives the left rotation.

**Rebinding `values` instead of mutating it.** `values = ...` inside the function changes nothing
the caller can see.

**A single-element list.** Any rotation leaves it alone, and the modulo makes `shift` zero.

## Cost

O(n) time, O(1) extra space.
