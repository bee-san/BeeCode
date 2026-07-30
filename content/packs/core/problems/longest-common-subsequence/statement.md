Return the length of the longest subsequence common to both `first` and `second`.

A subsequence keeps the original order but may skip characters. A common subsequence is one
that appears in both strings.

## Constraints

- `1 <= len(first), len(second) <= 1000`
- Both strings are lowercase `a`-`z`.

## Follow-up

Compare the two strings one character at a time from the end. If the last characters match,
they can both be used and the problem shrinks on both sides at once. If they do not, one of
the two strings must give up its last character — and you cannot tell which without trying
both. That is the recurrence; the table is what stops it being exponential.
