`rows` is a triangle: row `i` has `i + 1` entries. From entry `j` of row `i` you may step to entry `j`
or entry `j + 1` of row `i + 1`.

Return the smallest total of a path from the single top entry to any entry of the bottom row.

## Constraints

- `1 <= len(rows) <= 200`
- Row `i` has exactly `i + 1` entries.
- `-10000 <= entry <= 10000`

## Follow-up

Working top-down needs a "which of my two parents can reach me" case analysis at both edges. Working
**bottom-up** has no edge cases at all, and needs only one row of storage. Why does the direction
make that difference?
