## The insight

Two cursors a fixed distance apart. Put the lead `n` positions ahead of the trail,
then move both at the same speed. When the lead falls off the end, the gap has been
preserved all along — so the trail is sitting exactly `n` positions from the end,
which is the element to remove.

```python
def remove_nth_from_end(values, n):
    lead, trail = n, 0
    while lead < len(values):
        lead += 1
        trail += 1
    return values[:trail] + values[trail + 1:]
```

That is the same fixed-offset idea as in the linked-list original, where it saves a
pass: without it you must walk once to measure the length and again to reach the
node.

## The dummy head

In the pointer version, removing the *first* node is the awkward case, because
there is no predecessor whose `next` you can rewrite. The standard fix is a dummy
node placed before the head:

```
dummy -> 1 -> 2 -> 3
```

Start the trail at the dummy, and "remove the first real node" becomes an ordinary
relink of `dummy.next`. Then return `dummy.next` as the new head. It removes the
special case rather than handling it, which is usually the better trade.

## Pitfalls

**Off by one on the offset.** `n = 1` must remove the last element. Setting the
lead to `n - 1` or stopping the loop at `<=` shifts the target by one, and the
symptom is that both ends of the list break at once.

**Removing the head.** `n == len(values)` targets index 0. Slicing handles it;
pointer code does not, without the dummy.

**Two passes.** Measuring the length first is correct and clear, and is a perfectly
good answer if the one-pass trick will not come to mind.

## Cost

O(n) time, O(1) extra space in the linked-list form.
