## The insight

Build the ordering one position at a time. For the position you are filling, try every
value you have not already placed; recurse to fill the rest; then undo.

```python
def extend():
    if len(order) == len(nums):
        found.append(list(order))
        return
    for index in range(len(nums)):
        if used[index]:
            continue
        used[index] = True
        order.append(nums[index])
        extend()
        order.pop()               # undo both changes,
        used[index] = False       # in the reverse order you made them
```

## The difference from subsets

Subsets asked a yes/no question per element and never revisited it. Permutations pick
*which element goes next*, so the loop is over candidates rather than a binary choice,
and you need `used` to avoid placing the same element twice.

**Undo everything you did.** Two mutations happen before the recursive call, so two
undos happen after it. Restoring `order` but not `used` produces a partial answer that
silently loses permutations; the reverse produces duplicates.

**Snapshot on append.** `found.append(order)` stores the same mutable list every time,
and it is empty when you return. `list(order)` is required.

## Why `used` and not `remove`

You could instead pass the remaining elements as a new list each call. It reads more
cleanly and allocates a list per node of the recursion tree. The `used` flags keep one
array alive for the whole search, which is the usual trade in backtracking: slightly
more bookkeeping, considerably less garbage.

## Cost

O(n · n!) time and output size — there are `n!` permutations and each is `n` long.
