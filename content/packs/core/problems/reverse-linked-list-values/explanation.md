## The insight

Reversal is `n // 2` swaps, not `n` moves.

The naive picture is "build a new sequence, walking the old one backwards". The better
picture is that reversal **pairs each position with its mirror**: index 0 with index
`n - 1`, index 1 with `n - 2`, and so on. Every pair needs exactly one swap, and once
the two pointers meet you are done. Nothing needs to be allocated, and each element is
touched once.

For odd lengths the middle element is its own mirror, so it needs no swap at all — the
loop condition `low < high` (strict) excludes it automatically. That is not a special
case you have to handle; it is a special case the right loop condition makes disappear.

## Swap inward

```python
def reverse_values(values):
    reversed_values = list(values)
    low, high = 0, len(reversed_values) - 1
    while low < high:
        reversed_values[low], reversed_values[high] = (
            reversed_values[high],
            reversed_values[low],
        )
        low += 1
        high -= 1
    return reversed_values
```

The `list(values)` copy is there because the entry point returns a value rather than
mutating in place; drop it and the loop is a genuine in-place reversal with O(1) extra
space.

Three things to get right:

**`low < high`, not `low <= high`.** With `<=` and an odd length, the middle element is
swapped with itself — harmless. But the deeper reason to use `<` is what happens if you
instead loop `for low in range(len(values))` and swap `low` with its mirror every time:
you run the full length, swap every pair twice, and end up with the original sequence
back. Half the range is not an optimisation, it is the correctness condition.

**Swap, do not overwrite.** Writing `values[low] = values[high]` and then
`values[high] = values[low]` loses the first value — the second line reads the value the
first line just clobbered. Python's tuple assignment evaluates the whole right-hand side
before binding anything, which is exactly why the one-line form is safe; in a language
without it you need an explicit temporary.

**Do not sort.** `[4, 4, 7, 4]` reverses to `[4, 7, 4, 4]`. Reversal has nothing to do
with order-by-value, and a test with repeated values catches anyone who conflated them.

For the linked-list version this same problem becomes a three-pointer walk — `previous`,
`current`, `next_node` — where you re-point each node's `next` at the node behind it and
must save `current.next` *before* overwriting it or you lose the rest of the chain. The
mirror-swap trick is unavailable there because you cannot reach index `n - 1` in O(1).
Worth writing out once by hand; the discipline of "save the pointer before you clobber
it" is the same instinct as the swap above.

## Cost

O(n) time, O(1) extra space for the in-place version.

In real code you would write `values[::-1]` or `list(reversed(values))`, both O(n) time and
O(n) space for the new list. Use them. Know the swap loop anyway, because it is the version
that generalises — to rotating a list, reversing a subrange, and the palindrome check.
