Design a structure that is initialised with `k` and a list of starting values, and
then supports one operation:

- `add(value)` — add the value to the collection and return the `k`th largest value
  it now contains.

Duplicates count separately, so in `[5, 5]` the second largest is `5`.

You may assume the collection holds at least `k` values whenever `add` is called.

Return a list holding the result of each `add`, in order.

## Constraints

- `1 <= k <= 10_000`
- `0 <= len(initial) <= 10_000`, and `len(initial) + len(additions) >= k`
- `-10**4 <= value <= 10**4`
- `1 <= len(additions) <= 10_000`

## Follow-up

Re-sorting on every `add` is O(n log n) each time. You never need the whole order —
only one value. Keeping a heap of just the `k` largest values makes `add` O(log k),
and it is a **min**-heap even though the question asks about the largest. Work out why
the smallest end is the useful one.
