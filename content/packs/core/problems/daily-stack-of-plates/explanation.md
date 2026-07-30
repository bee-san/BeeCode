## The insight

Keep a running total alongside the list. `push` adds to it, `pop` subtracts from it, `sum` reads it.

```python
def push(value):
    held.append(value)
    total += value

def pop():
    if not held:
        return None
    value = held.pop()
    total -= value
    return value
```

Every operation is O(1), including `sum`.

## Why a total can be repaired but a minimum cannot

Addition has an inverse. Removing a value's contribution to a sum is a subtraction, so the running
total can be corrected on `pop` using only the value being removed.

Minimum has no inverse. If the value being popped *was* the minimum, the new minimum is some other
value in the stack, and nothing about the popped value tells you which — the information was never
kept. That is why [A Stack That Knows Its Minimum](min-stack) has to store a minimum *per entry*,
whereas here a single number suffices.

This is the general shape: a running aggregate is O(1)-maintainable under removal exactly when the
combining operation is invertible. Sum and count and XOR are; min, max, and "distinct count" are
not.

## Floating point would change the answer

With floats, repeated `+=` and `-=` accumulate rounding error, and after enough operations the
running total drifts from the true sum. Integers are exact, so it does not arise here. The general
lesson stands: invertibility on paper is not invertibility in arithmetic.

## Why `pop` returns `null` rather than raising

The replay protocol needs a value per query, and a sentinel keeps the result list aligned with the
operations. A real API would more likely raise, and the statement says which convention applies
rather than leaving the tests to imply it.

## Pitfalls

**Recomputing the sum on demand.** Correct, and O(n) per call.

**Not adjusting the total on `pop`.** The stack and the total drift apart, and only a `sum` after a
`pop` catches it.

**Popping an empty stack.** Return the sentinel; do not touch the total.

**Emitting a result for `push`.** The output holds one entry per query only.

## Cost

O(1) per operation, O(n) space.
