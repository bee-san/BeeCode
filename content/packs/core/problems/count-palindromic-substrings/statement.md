Return how many **contiguous** substrings of `text` are palindromes.

Substrings at different positions count separately even if they read the same, and every
single character is a palindrome.

## Constraints

- `1 <= len(text) <= 1000`
- `text` is lowercase `a`-`z`.

## Follow-up

Same structure as [Longest Palindromic Substring](longest-palindromic-substring), with the
counting done differently. When you expand from a centre, each successful widening step *is*
a distinct palindrome — so the count falls out of the loop rather than needing a second
pass.
