Design a fixed-capacity cache that evicts the **least recently used** entry when it
is full.

- `get(key)` returns the value, or `-1` if the key is absent.
- `put(key, value)` inserts or overwrites. If this pushes the number of entries past
  `capacity`, evict the least recently used key first.

Both a successful `get` and any `put` count as a use of that key.

Because BeeCode tests functions rather than classes, replay a list of operations.
Each is a list:

- `["put", key, value]`
- `["get", key]`

Return a list holding the result of each `get`, in order.

## Constraints

- `1 <= capacity <= 3000`
- `1 <= len(operations) <= 20_000`
- Keys and values are integers, `0 <= key, value <= 10**6`
- Both operations must run in O(1) expected time.

## Follow-up

The O(1) requirement is the Problem. A hash map alone cannot tell you which key is
least recently used; a list that tracks order cannot find a key cheaply. Which two
structures, and what does each one hold so that "move this key to most recent" is a
constant-time operation?
