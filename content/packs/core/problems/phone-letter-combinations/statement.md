On a telephone keypad the digits `2` to `9` each carry a group of letters:

```text
2 -> abc    3 -> def    4 -> ghi    5 -> jkl
6 -> mno    7 -> pqrs   8 -> tuv    9 -> wxyz
```

Given a string of such digits, return every letter string it could spell, taking one
letter per digit in order.

An empty input spells nothing, so return an empty list. The order of the results is not
judged.

## Constraints

- `0 <= len(digits) <= 4`
- Every character of `digits` is between `2` and `9`.

## Follow-up

This is a **Cartesian product**: one independent choice per digit, so the result count is
the product of the group sizes. Write the recursion, then write the iterative version
that grows the answer set one digit at a time — they are the same computation in
different shapes.
