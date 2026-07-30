There are `n` courses labelled `0` through `n - 1`. Each pair `[course, prerequisite]`
in `prerequisites` means you must take `prerequisite` before `course`.

Return `True` if it is possible to take every course, and `False` otherwise.

## Constraints

- `1 <= n <= 100_000`
- `0 <= len(prerequisites) <= 200_000`
- Both entries of each pair are valid course labels.
- Pairs may repeat.

## Follow-up

The answer is `False` exactly when the prerequisite graph contains a cycle — you can
never start a course that ultimately requires itself. Kahn's algorithm decides this by
*trying* to build a valid order and noticing if it stalls. What does the number of
courses it managed to schedule tell you?
