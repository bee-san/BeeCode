## The insight

Binary addition of two bits gives a sum bit and a carry bit:

- The sum bit, ignoring carries, is `a ^ b`.
- A carry is generated wherever both bits are set: `(a & b) << 1`.

So repeat "add without carrying, then fold the carry back in" until there is no carry:

```python
while b:
    carry = (a & b) << 1
    a = a ^ b
    b = carry
return a
```

Each round moves every carry at least one position left, so within the word width the carries run
off the end and the loop terminates.

## Why Python needs a mask

In a fixed-width language the loop above is the whole answer. Python's integers are unbounded and
negative numbers behave as if they have infinitely many leading `1` bits, so a negative
intermediate keeps generating carries forever.

Masking with `0xFFFFFFFF` after each step confines everything to 32 bits, which is what a real
machine word does automatically. Then one conversion at the end: if the result exceeds
`0x7FFFFFFF` the sign bit is set, so reinterpret the pattern as a negative number.

Working around the language rather than with it is unusual, and it is worth noticing that the
awkwardness is Python's, not the algorithm's.

## Reading the sign conversion

```python
if a <= 0x7FFFFFFF:
    return a
return ~(a ^ mask) & mask | ~mask
```

`a ^ mask` flips all 32 bits, giving the magnitude minus one; the rest restores the sign bits
above bit 31 so Python sees a negative integer. Equivalently `a - (1 << 32)`, which uses the
subtraction the problem forbids — hence the bit form.

## Pitfalls

**Not masking.** An infinite loop the moment either operand is negative, and it will not show up
on `2 + 3`.

**Masking `a` but not `b`.** The carry grows without bound.

**Shifting the XOR rather than the AND.** The carry comes from the AND.

**Returning the raw pattern for a negative result.** You get a large positive number instead of a
small negative one.

## Cost

O(word width) — at most 32 rounds — and O(1) space.
