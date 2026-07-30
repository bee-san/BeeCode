Given `n`, return every string of `n` opening and `n` closing parentheses that is
balanced.

A string is balanced when every `(` has a matching `)` that comes after it — the
same condition as in [Valid Parentheses](valid-parentheses), restricted to one
bracket type.

For `n = 3` there are five: `"((()))"`, `"(()())"`, `"(())()"`, `"()(())"`,
`"()()()"`.

The order of the strings in your answer does not matter.

## Constraints

- `0 <= n <= 8`
- For `n = 0` return `[""]` — the empty string is balanced.

## Follow-up

Generating all `2^(2n)` strings and filtering wastes almost all of the work. Two
running counts are enough to know, at every step, which characters are still
legal.
