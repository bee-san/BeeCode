## The insight

Walk in strides of `k`, and only act when a full stride is available:

```python
def reverse_groups(values, k):
    result = []
    start = 0
    while start + k <= len(values):
        result.extend(reversed(values[start:start + k]))
        start += k
    result.extend(values[start:])
    return result
```

`start + k <= len(values)` is the whole specification of "a full group exists". The
final `extend` copies whatever short tail is left, in order.

## The linked-list version

This is where the Problem earns its rating. Three connections must be rewired per
group and it is easy to lose one.

1. **Count ahead.** Walk `k` nodes from the current position. If you fall off the
   end, stop — the rest of the list stays as it is. Do this *before* reversing;
   reversing and then discovering the group was short means unpicking your own work.
2. **Reverse the `k` nodes.** The standard three-pointer loop, run exactly `k`
   times rather than to the end of the list.
3. **Rejoin.** The node before the group must now point at what was the group's
   last node; the group's original first node — now its last — must point at the
   node after the group.

A dummy head before the list removes the special case of the very first group,
whose predecessor would otherwise not exist. Keep a `group_previous` pointer to the
node before the current group and the bookkeeping stays manageable.

The recursive form is much shorter — reverse the first `k`, then set the tail's
`next` to the result of recursing on the remainder — at the cost of O(n/k) stack
depth.

## Pitfalls

**`k = 1`.** Every group is a single element, so nothing changes. The stride loop
handles it; special-casing usually breaks it.

**`k` larger than the list.** No full group exists, so the list is returned
unchanged — not reversed.

**Reversing the short tail.** The most common wrong answer, and the reason step one
comes first.

**`k` exactly dividing the length.** Then there is no tail, and `values[start:]` is
empty. Fine here; in pointer code it is where a missing null check appears.

## Cost

O(n) time — each element is visited a constant number of times. O(1) extra space in
the linked-list version, since reversal only relinks nodes.
