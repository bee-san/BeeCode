## The insight

The definition of depth is already recursive: the depth of a tree is one more than
the depth of its deeper subtree. An empty tree contributes nothing. Write that down
and you are finished.

```python
def max_depth(tree):
    def depth(index):
        if index >= len(tree) or tree[index] is None:
            return 0
        return 1 + max(depth(2 * index + 1), depth(2 * index + 2))

    return depth(0)
```

Two details do the work:

**One base case, not two.** It is tempting to special-case "this node is a leaf".
You do not need to: a leaf's children both fall off the end or are `None`, both
recursive calls return `0`, and the leaf correctly reports `1`. Fewer cases, fewer
places to be wrong.

**Guard the index before you index.** `index >= len(tree)` must be checked first, or
a node near the bottom of the list raises `IndexError` instead of reporting `0`.

## Cost

O(n) time — every reachable index is visited once. Space is O(h) for the call stack,
where `h` is the height: O(log n) on a balanced tree, but O(n) on a chain, which is
why a very deep tree would want an explicit stack or a level-by-level walk instead
of recursion.
