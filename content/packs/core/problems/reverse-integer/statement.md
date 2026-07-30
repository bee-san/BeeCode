Reverse the decimal digits of a signed integer, keeping the sign.

If the reversed value falls outside the signed 32-bit range `[-2^31, 2^31 - 1]`, return `0`
instead.

## Constraints

- `-2^31 <= number <= 2^31 - 1`

## Follow-up

Peeling digits with `% 10` and `// 10` is the easy half. The hard half is the range check, and the
honest version detects the overflow **before** it happens rather than building the value and
looking at it afterwards. What comparison tells you the next step will overflow?
