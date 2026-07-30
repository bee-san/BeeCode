## The insight

Walk from the last digit towards the first:

- A digit below `9` can absorb the increment. Add one and stop — nothing to the left changes.
- A `9` becomes `0` and the carry moves left.

If the walk falls off the front, every digit was a `9`, so the answer is a `1` followed by that
many zeroes:

```python
result = list(digits)
for index in range(len(result) - 1, -1, -1):
    if result[index] < 9:
        result[index] += 1
        return result
    result[index] = 0
return [1] + result
```

## The early return is the algorithm

Stopping at the first non-`9` is what makes this O(1) in the common case. Only a suffix of nines
is ever touched, and only all-nines makes the result longer. The `[1] + result` line runs for
`[9]`, `[9, 9]`, `[9, 9, 9]` — and for nothing else, which is why those are the interesting
tests.

## Why not convert to an integer

`int("".join(...)) + 1` works in Python because integers are arbitrary precision. In a language
with fixed-width integers a 100-digit input overflows, and the digit walk does not. Doing it
digit by digit is the transferable answer, and it is also strictly less work.

## Copying the input

Mutating the argument would be visible to the caller. Copying first keeps the function pure,
which matters more here than the allocation costs.

## Pitfalls

**Not handling all nines.** Returns all zeroes.

**Carrying past the first non-nine.** The early return prevents it.

**Walking left to right.** The carry propagates the other way.

**`[0]`.** Becomes `[1]`; the first digit is below `9`, so the ordinary path handles it.

## Cost

O(n) worst case, O(1) typical; O(n) space for the result.
