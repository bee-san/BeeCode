An abbreviation replaces any number of non-overlapping, non-adjacent substrings with their lengths.
So `"substitution"` may be written `"s10n"`, `"sub4u4"`, or `"12"`.

Return whether `abbreviation` is a valid abbreviation of `word`.

A number in the abbreviation must not have a leading zero, and `0` itself is never a valid length.

## Constraints

- `1 <= len(word) <= 20`
- `1 <= len(abbreviation) <= 20`
- `word` is lowercase letters; `abbreviation` is lowercase letters and digits.

## Follow-up

Two pointers, one per string. A letter must match; a digit begins a number that has to be parsed in
full before skipping. Both pointers must finish exactly at the end — and the leading-zero rule is
what stops `"01"` from being read as a skip of one.
