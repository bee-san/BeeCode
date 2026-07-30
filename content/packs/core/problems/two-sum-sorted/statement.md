`numbers` is sorted in non-decreasing order. Find the two entries that add up to
`target` and return their indices as a list `[i, j]` with `i < j`.

Indices are **0-based**. Exactly one pair works, and you may not use the same
element twice.

## Constraints

- `2 <= len(numbers) <= 100_000`
- `numbers` is sorted ascending; values may repeat.
- `-1000 <= numbers[i] <= 1000` and `-2000 <= target <= 2000`
- Exactly one valid pair exists.

## Follow-up

A hash map solves this in O(n) time and O(n) space, exactly as for the unsorted
version — but then the sortedness was wasted. Use it, and get to O(1) space.
