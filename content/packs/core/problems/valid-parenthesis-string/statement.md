`text` contains `(`, `)` and `*`. Each `*` may stand for `(`, for `)`, or for nothing at all.

Return `True` if some choice of meanings makes the string balanced: every `(` matched by a
later `)`, and no `)` unmatched.

## Constraints

- `0 <= len(text) <= 100`
- `text` contains only `(`, `)` and `*`.

## Follow-up

A single running counter cannot work, because a `*` makes the count ambiguous. But you do not
need to know the exact count — only the *range* of counts still possible. Two numbers, then.
