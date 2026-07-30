## The insight

Visiting every node gives the right sum, but throws away the only thing that makes
this a *search* tree. The ordering lets you answer a question about a subtree
without descending into it:

- If a node's value is `<= low`, everything in its **left** subtree is smaller
  still, so none of it can be in range. Do not go left.
- If a node's value is `>= high`, everything in its **right** subtree is larger,
  so none of it can be in range. Do not go right.

Notice this prunes independently of whether the node *itself* counts. A node below
`low` contributes nothing but may still have in-range descendants on its right.

## The walk

```python
def range_sum(tree, low, high):
    total = 0
    pending = [0]
    while pending:
        index = pending.pop()
        if index >= len(tree) or tree[index] is None:
            continue
        value = tree[index]
        if low <= value <= high:
            total += value
        if value > low:
            pending.append(2 * index + 1)
        if value < high:
            pending.append(2 * index + 2)
    return total
```

Two easy mistakes:

**Pruning on the wrong side.** `value > low` guards the *left* child, not the
right. Getting these crossed still returns the correct sum on small symmetric
trees, which makes it a bug that passes a casual test.

**Forgetting the bounds are inclusive.** `low < value < high` silently drops any
node sitting exactly on a bound, and a range like `[3, 3]` then returns 0.

## Cost

O(n) worst case — a range covering everything must visit everything — but the
pruning is what makes it much better than that in practice: only nodes whose
subtree could contain an in-range value are ever touched. For a narrow range on a
balanced tree that is about `log n` nodes plus the matches themselves.

The unpruned full scan is always O(n), no matter how narrow the range.
