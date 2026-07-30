Design a structure supporting two operations on a growing collection of numbers:

- `add(value)` — include the value.
- `median()` — the median of everything added so far. With an even count that is the
  mean of the two middle values.

Because BeeCode tests functions rather than classes, replay a list of operations. Each
is a `[name, argument]` pair: `["add", value]` or `["median", null]`.

Return a list holding the result of each `median`, in order. A `median` is never
called on an empty collection.

## Constraints

- `1 <= len(operations) <= 50_000`
- `-10**5 <= value <= 10**5`
- A median may be a `.5` value, so results are compared with a numeric tolerance.

## Follow-up

Sorting on every query is O(n log n) each time. You do not need the collection
ordered — only its middle. Split it into a lower half and an upper half, and keep the
boundary between them cheaply reachable. Which kind of heap for each half?
