## The insight

`number & (number - 1)` clears the lowest set bit and leaves every other bit alone. So looping
until the value reaches zero counts the set bits, one per iteration:

```python
total = 0
while number:
    number &= number - 1
    total += 1
return total
```

## Why that identity holds

Subtracting one flips the lowest set bit to `0` and turns every zero below it into a `1`. The bits
above are untouched, because the borrow stops at the first `1` it finds.

```text
number      = 1011 0100
number - 1  = 1011 0011
AND         = 1011 0000
```

Above the lowest set bit the two agree, so the AND keeps them. At the lowest set bit they differ
(`1` and `0`), so it clears. Below it they are exact complements (`0`s against `1`s), so those
clear too. The lowest set bit is gone and nothing else moved.

## The straightforward version

```python
total = 0
for _ in range(32):
    total += number & 1
    number >>= 1
return total
```

Always 32 iterations. Correct, and worth being able to write, but the clearing trick costs one
iteration per set bit — so 1 step for `128` rather than 32.

## Why not `bin(number).count("1")`

It works in Python and is a perfectly good answer to the practical question. It is not an answer
to the question being trained here, which is what `n & (n - 1)` does — an identity that comes back
in [Count the Bits Up to a Number](counting-bits), in power-of-two tests, and in bitset loops.

## Pitfalls

**Using `number > 0` with a shift on a signed type.** In a language with arithmetic right shift,
shifting a negative number sign-extends forever. Python's integers are unbounded and the inputs
here are non-negative, so the loop terminates — but the `&` version sidesteps the question
entirely.

**Zero.** Zero set bits; the loop body never runs.

**Forgetting the assignment.** `number & number - 1` computes the value and discards it, so the
loop never ends.

## Cost

O(set bits) time — at most 32 here — and O(1) space.
