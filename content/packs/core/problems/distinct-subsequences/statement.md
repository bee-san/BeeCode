Return how many distinct subsequences of `text` equal `pattern`.

A subsequence keeps order but may skip characters. Two subsequences are distinct if they use
different **positions** of `text`, even when the characters they pick are identical.

## Constraints

- `1 <= len(text) <= 1000`
- `1 <= len(pattern) <= 1000`
- Both strings are lowercase `a`-`z`.
- The answer fits in a 64-bit signed integer.

## Follow-up

At each character of `text` you either use it to satisfy the next character of `pattern`, or
skip it. When the characters match, *both* choices are open — and both must be counted, not
chosen between. That is the difference between this and
[Longest Common Subsequence](longest-common-subsequence).
