Given a string `s`, return the length of the longest **contiguous substring** that
contains no repeated character.

## Constraints

- `0 <= len(s) <= 50_000`
- `s` may contain any printable ASCII characters, including spaces and digits.
- Your solution must run in O(n) time.

## Follow-up

Note the difference between a *substring* and a *subsequence*. For `"pwwkew"` the
answer is `3` (`"wke"`), not `4` — `"pwke"` skips a character, so it is a subsequence
and does not count.

The O(n) requirement rules out re-scanning the window on every step. The interesting
case is what happens when you meet a duplicate: how far does the window's left edge
have to jump, and can it ever be made to move *backwards*?
