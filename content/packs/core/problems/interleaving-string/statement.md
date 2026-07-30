Return `True` if `whole` can be formed by interleaving `first` and `second`.

An interleaving takes the characters of both strings, keeping each string's own order, and
merges them. Every character of both strings must be used exactly once.

## Constraints

- `0 <= len(first), len(second) <= 100`
- `0 <= len(whole) <= 200`
- All strings are lowercase `a`-`z`.

## Follow-up

Each character of `whole` came from one of the two strings, and when both offer the same
character next, greedily picking one can dead-end while the other would have worked. What
pair of numbers fully describes the state you are in partway through?
