`values` is sorted strictly ascending with no duplicates. Return the shortest list of range strings
that covers exactly the values present.

Each range is written as `"start->end"`, or just `"start"` when it holds a single value. Ranges
appear in ascending order and none of them may include a value absent from `values`.

## Constraints

- `0 <= len(values) <= 20`
- `-2^31 <= values[i] <= 2^31 - 1`
- Strictly ascending.

## Follow-up

Walk the list, extending the current run while each value is exactly one more than the last. The
only decision is where a run ends — and then how to format a run of length one differently from
the rest.
