Letters are encoded as numbers: `"a"` is `1`, `"b"` is `2`, and so on up to `"z"` being
`26`.

Given a string of digits, return how many different letter strings could have produced it.

A digit group has no leading zero, so `"06"` cannot decode as `"f"`, and `"0"` alone cannot
decode at all.

## Constraints

- `1 <= len(digits) <= 100`
- `digits` contains only the characters `0`-`9`.

## Follow-up

Reading left to right, each step consumes either one digit or two. That gives a recurrence
much like [Climbing Stairs](climbing-stairs) — with the difference that each of the two
steps is only available when the digits it consumes are actually valid. What makes a
one-digit step invalid, and what makes a two-digit step invalid?
