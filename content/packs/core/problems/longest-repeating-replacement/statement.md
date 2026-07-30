You may change at most `k` characters of the string `s`, each to any uppercase
letter you like. Return the length of the longest run of a **single repeated
character** you can produce.

For `s = "AABABBA"` and `k = 1` the answer is `4`: change the `A` at index 3 to
`B` and `"BABB"` becomes `"BBBB"`.

## Constraints

- `1 <= len(s) <= 100_000`
- `s` contains only uppercase English letters.
- `0 <= k <= len(s)`

## Follow-up

A window is convertible into a single repeated character when the number of
characters that are *not* the window's most common one is at most `k`. Write that
as a formula in the window's length and you have your window condition.
