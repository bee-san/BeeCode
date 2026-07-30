## The insight

Carry the furthest index reachable so far. Walk left to right; at each index, first check that
you could have got there at all, then extend the reach:

```python
furthest = 0
for index in range(len(jumps)):
    if index > furthest:
        return False
    furthest = max(furthest, index + jumps[index])
return True
```

If the loop finishes, every index was reachable, including the last.

## Why the greedy is safe

Reachability is *prefix-closed*: if you can reach index `i`, you can reach every index before
it, since jumps may be shorter than the maximum. So there is never a trade-off — a longer reach
is strictly better, and taking the maximum at every step cannot rule out a route. That is what
makes one number enough, and it is why this needs no DP table.

The check must come **before** the extension. Reading `jumps[index]` for an unreachable index
would let a later large value rescue a route that was already dead: `[3, 2, 1, 0, 4]` returns
`True` if you extend first, because the `4` at index 4 is never actually reachable.

## The shortest form

Because the loop returns `False` the moment `index > furthest`, and `furthest` only grows, this
is a single pass. An early `if furthest >= len(jumps) - 1: return True` is a legitimate
shortcut but changes nothing asymptotically.

## Pitfalls

**Extending before checking reachability.** Accepts unreachable inputs.

**A single element.** `True` — you are already at the end, whatever `jumps[0]` is, including
`0`.

**Assuming jumps must be exact.** Any distance up to `jumps[i]` is allowed, which is precisely
what makes reachability prefix-closed.

**Recursing over every possible jump length.** Exponential, and unnecessary.

## Cost

O(n) time, O(1) space.
