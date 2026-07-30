There are `n` courses labelled `0` through `n - 1`. Each pair `[course, prerequisite]` in
`prerequisites` means `prerequisite` must be taken before `course`.

Return **an** order in which all `n` courses can be taken. If no such order exists,
return an empty list.

Several valid orders usually exist, and any one of them is accepted.

## Constraints

- `1 <= n <= 2000`
- `0 <= len(prerequisites) <= 2000`
- Both entries of each pair are valid course labels.
- Pairs may repeat.

## How your answer is judged

Because any valid order is acceptable, the tests use inputs whose answer is **forced** —
a chain of prerequisites admits exactly one order — or where the only question is whether
you return an order at all, so the expected value is unambiguous. In an interview, say out
loud that the order is not unique.

## Follow-up

[Course Schedule](course-schedule) asks only whether an order exists. Producing one is the
same algorithm with the by-product kept instead of discarded — and the emptiness check
becomes "did I manage to schedule all `n`?"
