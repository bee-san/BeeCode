`triplets` holds triples of numbers. You may repeatedly pick two triples and replace them with
their **position-wise maximum**: merging `[a, b, c]` and `[d, e, f]` gives
`[max(a, d), max(b, e), max(c, f)]`.

Return `True` if some sequence of merges produces exactly `target`.

Each merge adds a triple; the originals stay available.

## Constraints

- `1 <= len(triplets) <= 100000`
- `1 <= triplets[i][j], target[j] <= 1000`

## Follow-up

The maximum never decreases, so a triple with any entry exceeding the target's is poison — once
merged in, that position can never come back down. Which triples are safe to use, and what do
you need from the safe ones?
