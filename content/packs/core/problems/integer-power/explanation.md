## The insight

Exponentiation by squaring reads the exponent in binary. Keep a running `value` that is
`base^(2^k)` for the bit position you are on, and multiply it into the result whenever that bit
is set:

```python
result, value, remaining = 1.0, float(base), exponent
while remaining > 0:
    if remaining % 2 == 1:
        result *= value
    value *= value
    remaining //= 2
return result
```

`base^13` is `base^8 * base^4 * base^1`, because 13 is `1101` — four squarings and three
multiplications instead of twelve.

## Negative exponents

Handle them once, at the top: `power(base, exponent) == 1 / power(base, -exponent)`. Doing it
first means the main loop only ever sees a non-negative exponent, which keeps the loop condition
simple.

The trap in a fixed-width language is that negating the most negative integer overflows. Python
has no such limit, but taking the reciprocal of the positive result — rather than negating and
hoping — is the habit that transfers.

## Why the recursive form is the same thing

```text
power(b, n) = power(b, n // 2)^2          if n is even
            = power(b, n // 2)^2 * b      if n is odd
```

O(log n) with O(log n) stack. The iterative loop is that recursion with the stack unrolled, and
neither is clearer than the other — but the iterative one has no depth limit.

## Floating point

Squaring compounds rounding error, so a result may differ in the last bits from what repeated
multiplication gives. That is why the tests compare with `approximate_numeric` rather than
demanding exact equality — the algorithm is right, the arithmetic is inexact, and pretending
otherwise would make a correct solution fail.

## Pitfalls

**Multiplying `exponent` times.** Correct and O(exponent); too slow at 1000 in a tight loop, and
the wrong answer to the question being asked.

**An exponent of `0`.** `1.0`, for any base — the loop body never runs.

**Recomputing `power(base, n // 2)` twice per level.** Turns O(log n) back into O(n); bind it to
a name.

**Forgetting `float`.** An integer base with a large exponent gives an exact but enormous integer
rather than a float.

## Cost

O(log exponent) multiplications, O(1) space iteratively.
