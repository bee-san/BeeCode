## The insight

The answer is the smallest value above `target`, so restate the problem as a
search that keeps the best candidate it has seen.

Stand at a node:

- If its value is **greater** than `target`, it is a candidate — remember it. Any
  *better* candidate is smaller than this one while still above `target`, and
  every smaller value lives to the left. Go left.
- If its value is **not** greater, it is useless and so is everything left of it.
  Go right.

Each step halves the search space, and the last candidate you remembered is the
answer. Nothing is ever revisited, and no explicit in-order traversal happens.

## The descent

```python
def inorder_successor(tree, target):
    if not tree or tree[0] is None:
        return None

    best = None
    index = 0
    while index < len(tree) and tree[index] is not None:
        value = tree[index]
        if value > target:
            best = value
            index = 2 * index + 1
        else:
            index = 2 * index + 2
    return best
```

Three things worth being careful about:

**Strictly greater.** `value >= target` would let a node equal to the target be
its own successor, which is wrong and only shows up when the target is present.

**`None` is a real answer, not a failure.** The largest value in the tree has no
successor. Returning `None` and returning "not found" are the same thing here,
which is why the candidate starts as `None` and is simply never overwritten.

**Do not stop when you find the target.** The interesting cases are the ones where
the answer is an *ancestor* — a node with no right child, whose successor you
already walked past on the way down. Carrying `best` is what handles this without
parent pointers or a stack.

## Cost

O(h) time and O(1) space, where `h` is the height: about `log n` on a balanced
tree, `n` on a chain.

The in-order-walk approach is O(n) time and O(h) space for its stack, and it does
strictly more work — it visits both subtrees at every node, when the ordering
already told it which one to skip.
