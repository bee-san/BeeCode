## The insight

Peel bits off the bottom of the input and stack them onto the bottom of the output, shifting the
output up each time. The first bit read gets shifted left 31 more times, so it lands at position
31 — which is exactly the reversal.

```python
result = 0
for _ in range(32):
    result = (result << 1) | (number & 1)
    number >>= 1
return result
```

## Why the loop count is fixed at 32

`while number:` looks tidier and is wrong. For `number = 1` it runs once, returning `1` instead of
`2147483648`. The leading zeroes carry information here: reversal is defined relative to a word
*width*, not to the number's significant bits. The width has to come from the problem statement,
because the value cannot supply it.

This is the whole content of the problem, and it is why `reverse_bits(1)` is the first test.

## The order within the loop body

Shift `result` first, then OR in the new bit. Doing it the other way — OR then shift — leaves the
last bit read one position too high and loses nothing off the top only by luck. Write the shift
and the OR as one expression and the ordering is fixed by the parentheses.

## Doing it in five steps instead of 32

Swap halves, then quarters, then bytes, then pairs, then single bits, each with a pair of masked
shifts:

```text
swap adjacent 16-bit halves, then 8-bit, then 4-bit, then 2-bit, then 1-bit
```

Five operations regardless of the input, and it is how a real implementation does it. Worth
knowing that it exists; the per-bit loop is what to write when asked.

## Pitfalls

**Looping while the input is non-zero.** Under-shifts every input with a leading zero.

**Assuming Python's integers are 32 bits.** They are not, so the width is yours to enforce; here
the fixed 32 iterations do it, and nothing above bit 31 can be produced.

**Reversing the decimal digits.** A different problem — see
[Reverse the Digits of an Integer](reverse-integer).

**Zero.** Reverses to zero.

## Cost

O(1) — 32 iterations regardless of input — and O(1) space.
