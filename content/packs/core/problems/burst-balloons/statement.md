`values` holds one balloon per entry. Bursting the balloon at position `i` earns

```text
left * values[i] * right
```

where `left` and `right` are its current neighbours **after** earlier bursts have removed
balloons. A missing neighbour — off either end — counts as `1`.

Burst every balloon, in whatever order you like, and return the greatest total.

## Constraints

- `1 <= len(values) <= 300`
- `0 <= values[i] <= 100`

## Follow-up

Thinking about which balloon to burst *first* is a trap: the moment it goes, the array
reshapes and the remaining subproblems are no longer independent. Ask instead which balloon
in a range is burst **last**. What does that choice guarantee about its neighbours?
