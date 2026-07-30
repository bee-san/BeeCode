`values` holds `n` distinct integers drawn from `0` to `n` inclusive — so exactly one value in
that range is absent. Return it.

## Constraints

- `1 <= len(values) <= 10000`
- Every entry is in `[0, n]` where `n == len(values)`, and no entry repeats.

## Follow-up

Sorting finds it in O(n log n) and a set finds it in O(n) time and O(n) space. There are two ways
to do it in O(n) time and O(1) space: one uses a sum, the other uses XOR. The XOR version has a
property the sum version lacks — what is it?
