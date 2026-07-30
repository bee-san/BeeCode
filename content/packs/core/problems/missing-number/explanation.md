## The insight

XOR every index together with every value, and start from `n`:

```python
result = len(values)
for index, value in enumerate(values):
    result ^= index ^ value
return result
```

The indices run `0` to `n-1`, and seeding with `n` makes the index side cover `0` to `n` — the
complete range. The value side covers the same range with one member absent. XOR is its own
inverse and is commutative, so everything present on both sides cancels, and the survivor is the
one number missing from the values.

## The sum version

`n * (n + 1) // 2 - sum(values)`. Same complexity, one line, and arguably clearer.

## What XOR has that the sum does not

No overflow. The sum of `0` to `n` grows quadratically, so in a fixed-width language a large `n`
overflows the accumulator before the subtraction happens — and the wrong answer looks perfectly
plausible. XOR never produces a value larger than its inputs, so it cannot overflow at all.

Python's unbounded integers make this moot *here*, which is exactly why it is worth saying out
loud rather than leaving to the tests: the reason to prefer XOR is not visible in the Python
results.

## Why not a set

`set(range(n + 1)) - set(values)` is O(n) time and correct. It is also O(n) space, which is what
the follow-up asks you to avoid. Nothing wrong with reaching for it first.

## Pitfalls

**Seeding `result` at `0`.** Then the index side covers only `0` to `n-1` and `n` can never be
the answer. `[0]` catches it.

**XORing values only.** Without the indices there is nothing for them to cancel against.

**Assuming the input is sorted.** It need not be, and neither method cares.

**Assuming the missing number is interior.** It may be `0` or `n`.

## Cost

O(n) time, O(1) space.
