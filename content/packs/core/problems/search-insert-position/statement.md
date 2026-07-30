`values` is sorted strictly ascending with no duplicates. Return the index of `target`, or — if it is
absent — the index at which it would have to be inserted to keep the list sorted.

## Constraints

- `0 <= len(values) <= 100000`
- Strictly ascending.
- `-10^9 <= values[i], target <= 10^9`

## Follow-up

This is a binary search whose "not found" answer carries information rather than being discarded.
When the loop ends without a match, one of the two boundary variables is already the answer. Which,
and can you say why without tracing an example?
