## The insight

XOR has two properties that together solve this outright:

- `x ^ x == 0` — a value cancels itself.
- `x ^ 0 == x` — zero is the identity.

And XOR is commutative and associative, so the order of the input does not matter. XOR
the whole list together: every paired value cancels its partner, `0` falls away, and
what survives is the value with no partner.

```python
unpaired = 0
for value in nums:
    unpaired ^= value
return unpaired
```

One pass, one integer of extra space, no hash table, no sorting, and nothing that can
overflow.

## The alternatives, and why they are worse here

**Hash set:** add on first sight, remove on second, and one element is left. Correct,
one pass, but O(n) space.

**Sum trick:** `2 * sum(set(nums)) - sum(nums)`. Correct in Python, whose integers are
arbitrary precision — but in a language with fixed-width integers, summing 100,000
values near `10^9` overflows. XOR never does, because it does not carry.

**Sorting:** O(n log n) and it modifies or copies the input to answer a question that
needs one pass.

## Cost

O(n) time and O(1) extra space.

## Worth knowing

The same idea generalises. If every value appeared *three* times but one, XOR would no
longer cancel, and you would count bits modulo 3 instead. The technique is "find what
does not cancel", and XOR is just the tool for the pairs case.
