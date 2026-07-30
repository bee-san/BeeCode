Return `True` if some **contiguous** substring of `haystack` is a rearrangement of
`needle`.

Equivalently: does `haystack` contain a window of length `len(needle)` with
exactly the same letter counts as `needle`?

`haystack = "eidbaooo"`, `needle = "ab"` gives `True`, because `"ba"` appears.

## Constraints

- `1 <= len(needle), len(haystack) <= 20_000`
- Both strings contain only lowercase English letters.
- If `needle` is longer than `haystack` the answer is `False`.

## Follow-up

Every candidate window has the same length, so this window slides rather than
grows. What is the cheapest way to keep "these two letter counts are equal" up to
date as one character enters and one leaves?
