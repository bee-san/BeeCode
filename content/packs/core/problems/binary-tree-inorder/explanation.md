## The insight

"In-order" names the position of the *visit* relative to the two recursive calls.
Pre-, in-, and post-order are the same three lines in three different orders, and
that is the whole idea:

```python
walk(left);  visit(node); walk(right)   # in-order
visit(node); walk(left);  walk(right)   # pre-order
walk(left);  walk(right); visit(node)   # post-order
```

So:

```python
def inorder(tree):
    result = []

    def walk(index):
        if index >= len(tree) or tree[index] is None:
            return
        walk(2 * index + 1)
        result.append(tree[index])
        walk(2 * index + 2)

    walk(0)
    return result
```

**Append into one shared list rather than concatenating returned lists.** Returning
`walk(left) + [value] + walk(right)` is prettier and quadratic: every level copies
every value it has collected so far. One accumulator keeps it linear.

**`is None`, not falsiness.** A node holding `0` is a real node. `if not tree[i]`
silently prunes it, and the traversal loses a subtree.

## Cost

O(n) time and O(n) space for the output, plus O(h) call stack.
