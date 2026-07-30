Evaluate an arithmetic expression in reverse Polish notation, given as a list of
tokens. Each token is either an integer written as a string, or one of `"+"`,
`"-"`, `"*"`, `"/"`.

An operator applies to the two values immediately before it, in order: the token
sequence `["3", "4", "-"]` means `3 - 4`, not `4 - 3`.

Division is integer division that **truncates towards zero**, so `-7 / 2` is `-3`.
Division by zero never occurs, and the expression is always well formed.

Return the resulting integer.

## Constraints

- `1 <= len(tokens) <= 10_000`
- Integer tokens fit in a signed 32-bit range.
- The expression is valid: exactly one value remains at the end.

## Follow-up

Python's `//` is floor division, not truncation. `-7 // 2` is `-4`. That
difference is the single most common wrong answer here — how do you get
truncation?
