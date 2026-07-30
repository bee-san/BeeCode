`weights` lists packages in the order they sit on a conveyor. Each day a ship takes packages from the
front, in order, until adding the next one would exceed its capacity.

Return the smallest capacity that gets every package shipped within `days` days.

Packages may not be reordered or split.

## Constraints

- `1 <= len(weights) <= 50000`
- `1 <= weights[i] <= 500`
- `1 <= days <= len(weights)`

## Follow-up

For a *given* capacity, counting the days needed is a single greedy pass. And "ships within `days`
days" is monotonic in the capacity: if a capacity works, every larger one works too. That makes the
answer itself binary-searchable — what are the tightest bounds you can start from?
