## The insight

Least-significant-first is exactly the order long addition wants. Walk both lists
together, add the two digits and the carry, keep the units and carry the tens.

```python
def add_digit_lists(left, right):
    digits = []
    carry = 0
    index = 0
    while index < len(left) or index < len(right) or carry:
        total = carry
        if index < len(left):
            total += left[index]
        if index < len(right):
            total += right[index]
        digits.append(total % 10)
        carry = total // 10
        index += 1
    return digits or [0]
```

The loop condition carries the whole design. `or carry` is what emits the leading
`1` of `99 + 1 = 100`; the two length tests are what let one number be longer than
the other without a separate copying phase.

## Pitfalls

**Stopping when both lists run out.** The classic failure. `[9, 9] + [1]` gives
`[0, 0]` — the answer 00 — because the last carry is dropped. `or carry` in the
condition, not a fix-up afterwards, is the clean way.

**Converting to `int` and back.** In Python this actually works, because integers
are arbitrary precision, and it is a legitimate answer here. It is also the one
approach that would fail in a language with fixed-width integers on 1000-digit
inputs, so it is worth being able to write the digit loop.

**Padding the shorter list first.** Fine, but mutating the caller's list to do it is
not.

**`[0] + [0]`.** Two zeros give `[0]`, not `[]` and not `[0, 0]`. The loop produces
one digit here, which is right; the `or [0]` guard is for defensiveness.

## Cost

O(max(len(left), len(right))) time and the same space for the output.
