Return whether `first` can be turned into `second` by consistently relabelling its characters.

Consistently means every occurrence of a character maps to the same character, and no two distinct
characters map to the same one. A character may map to itself.

## Constraints

- `1 <= len(first), len(second) <= 50000`
- Both strings contain printable ASCII characters.

## Follow-up

One map from `first` to `second` is not enough, because it permits two characters to collapse onto
one. What does the second map buy you, and can a single map plus a set of taken targets do the same
job?
