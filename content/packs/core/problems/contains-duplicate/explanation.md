## The insight

"Does any value repeat?" sounds like a question about pairs, which is why the
instinct is to compare every pair. But you never need to hold two values side by
side. Walk the list once and ask a question about a single value at a time: **"have
I seen this before?"**

A set answers that in O(1). Once you frame it as a membership question rather than
a comparison question, the O(n²) loop disappears.

## One pass

```python
def contains_duplicate(nums):
    seen = set()
    for value in nums:
        if value in seen:
            return True
        seen.add(value)
    return False
```

There is a well-known one-liner that says the same thing:

```python
def contains_duplicate(nums):
    return len(set(nums)) != len(nums)
```

It is correct and it is idiomatic Python, but it always consumes the whole list.
The explicit loop returns the moment it finds the repeat, which matters when the
duplicate is near the front of a very long list. Know both; reach for the loop when
you want the early exit, and know why they differ.

Two things to watch:

**Check before you add.** Adding first and then testing membership makes every
element look like a duplicate of itself. Same trap as Two Sum, same fix: the check
comes first.

**Don't use a list for `seen`.** `value in some_list` is a linear scan, so that
version is still O(n²) — it just looks like the fast one. The O(1) membership test
is the entire point of the set.

## Cost

O(n) time, O(n) space. You trade memory for a linear scan.

If you are not allowed the memory, sort `nums` first and then check adjacent
elements: after sorting, equal values are neighbours, so one pass over the sorted
list finds any repeat. That is O(n log n) time and O(1) extra space if you sort in
place. Slower, but it is the right answer when memory is the binding constraint.
