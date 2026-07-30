## The insight

Peel the last digit off the input and push it onto the end of the result:

```python
digit = remaining % 10
remaining //= 10
result = result * 10 + digit
```

Take the sign off first and put it back at the end, so the digit peeling only ever deals with
non-negative values. Python's `%` and `//` on negatives round towards negative infinity, which
gives correct-but-surprising digits, and separating the sign avoids the question.

## The range check is the actual problem

`result = result * 10 + digit` is the step that can overflow, so check before performing it:

- If `result > limit // 10` — that is, above 214748364 — then multiplying by ten already exceeds
  the limit.
- If `result == limit // 10`, the multiply is exactly 2147483640, so the last digit may be at most
  `7`.

`limit` is 2147483647, whose last digit is `7`, hence the constant.

Testing after the fact — computing `result * 10 + digit` and then comparing — works in Python but
is exactly the check that cannot be written in a fixed-width language, because the value you want
to inspect has already wrapped. Checking first is the version that transfers.

## The asymmetric range

`[-2^31, 2^31 - 1]` has one more negative value than positive, so in principle the negative side
allows a final digit of `8`. In practice it cannot arise: a reversal ending in `8` would need an
input starting with `8`, and `-2147483648` reversed is `-8463847412`, far out of range anyway. The
final `signed` check catches anything the per-step guard lets through, so the code is right either
way — and this is the kind of edge worth checking rather than assuming.

## Trailing zeroes disappear

`120` reverses to `21`, not `021`. That falls out of the arithmetic — a leading zero contributes
`0 * 10^k` — and it is not reversible: `21` reverses back to `12`. Nothing to fix, but the tests
say so explicitly.

## Pitfalls

**Reversing the string and calling `int` on it.** Works in Python, sidesteps both the sign
handling and the overflow check, which are the content.

**Checking the range only at the end.** Correct in Python, impossible in C.

**Peeling digits from a negative number directly.** `-123 % 10` is `7` in Python.

**Forgetting `0`.** The loop never runs and `0` is returned.

## Cost

O(number of digits) time, O(1) space.
