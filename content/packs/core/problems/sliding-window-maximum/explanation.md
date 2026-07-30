## The insight

Ask which values in the window could *ever* be the answer, now or later.

If some earlier value is smaller than a later one, it is finished. The bigger value sits
to its right, so it stays in the window at least as long, and it wins every comparison
between them. The smaller one can never be the maximum again.

So keep a deque of indices whose values are **strictly decreasing**. The front is the
window's maximum; everything behind it is the standby if the front expires.

```python
candidates = deque()
for index, value in enumerate(nums):
    while candidates and candidates[0] <= index - k:   # front slid out of the window
        candidates.popleft()
    while candidates and nums[candidates[-1]] <= value: # value beats these forever
        candidates.pop()
    candidates.append(index)
    if index >= k - 1:
        maxima.append(nums[candidates[0]])
```

Two different removals happen at the two ends, and confusing them is the usual bug:

- **`popleft`** because the front is too *old* — an index-based check.
- **`pop`** because the back is too *small* — a value-based check.

## Details that matter

**Store indices, not values.** Expiry is a question about position; a deque of values
cannot tell you when the front left the window.

**Use `<=` when popping the back.** With `<`, equal values pile up. Not wrong for the
maximum itself, but the deque grows without bound on constant input, and the `all-equal`
test is there to keep you honest about it.

**Only emit once the first window is complete**, at `index >= k - 1`. Emitting earlier
reports maxima of partial windows and returns too many entries.

## Cost

O(n) time — each index is appended once and removed once, so the nested `while` loops
do at most `2n` operations in total. O(k) space.
