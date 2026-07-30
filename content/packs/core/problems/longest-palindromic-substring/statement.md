Return the longest **contiguous** substring of `text` that reads the same forwards and
backwards.

If several substrings tie for longest, return the one that starts earliest.

## Constraints

- `1 <= len(text) <= 1000`
- `text` is lowercase `a`-`z`.

## Follow-up

Checking every substring is O(n^3). The key structural fact is that a palindrome is
symmetric about its centre — and a string of length `n` has `2n - 1` centres, not `n`.
Where does the extra `n - 1` come from?
