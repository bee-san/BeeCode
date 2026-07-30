Design a store that remembers every value a key has ever held, tagged with the
timestamp at which it was set, and can answer "what was this key's value at time
`t`?".

A `get` at time `t` returns the value from the **largest timestamp not exceeding
`t`**. If the key has no value at or before `t`, return `""`.

Because BeeCode tests functions rather than classes, replay a list of operations.
Each is a list:

- `["set", key, value, timestamp]`
- `["get", key, timestamp]`

Return a list holding the result of each `get`, in order.

## Constraints

- `1 <= len(operations) <= 20_000`
- Keys and values are non-empty lowercase strings.
- `1 <= timestamp <= 10**7`
- For any one key, `set` calls arrive in **strictly increasing** timestamp order.

## Follow-up

The timestamps for a key arrive already sorted, which is worth noticing: it means
you never have to sort, and `get` is a search for a boundary rather than for an
exact match. Which boundary?
