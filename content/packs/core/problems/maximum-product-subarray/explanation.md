## The insight

Track **two** running values: the largest product ending here, and the smallest. A negative
number swaps their roles, so the most negative running product is a genuine asset — one more
negative turns it into the largest.

```python
candidates = (value, largest * value, smallest * value)
largest, smallest = max(candidates), min(candidates)
best = max(best, largest)
```

Including `value` on its own is what lets a run start fresh, which matters after a zero or
whenever the running products are worse than starting over.

## Why the minimum has to be carried

`[-2, 3, -4]`: after `-2` the largest is `-2`. At `3` the largest is `3` and the smallest is
`-6`. At `-4`, the largest becomes `-6 * -4 = 24`, which came entirely from the *smallest*
running product. Track only the maximum and the answer is `3`.

This is exactly what [Largest Sum of a Contiguous Run](max-subarray) does not need, because
addition preserves order — adding a value to a larger running sum keeps it larger.
Multiplication by a negative reverses it.

## Compute both before assigning either

`largest` and `smallest` must both be derived from the *previous* pair. Overwriting
`largest` first and then using it to compute `smallest` uses a value from the wrong step,
and the bug hides on inputs with at most one negative.

## Zeroes reset

At a zero, all three candidates involving it are zero, so both running values become zero
and the next value effectively starts a new run. `[0, 2]` gives 2 without any special case,
because `value` is always a candidate.

## Pitfalls

**Initialising `best` to `0` or `1`.** Both are wrong on an all-negative input like `[-3]`,
where the answer is `-3`. Start from `values[0]`.

**Tracking only the maximum.** Fails on the two-negatives case.

**Ordering the assignments wrongly.** As above.

**Dividing to slide a window.** A zero makes it undefined, and the values are integers.

## Cost

O(n) time, O(1) space.
