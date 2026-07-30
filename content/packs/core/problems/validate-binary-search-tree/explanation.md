## The insight

The tempting solution compares each node with its two children. It is wrong, and the
Problem's follow-up is the counterexample: a node can be larger than its parent yet
still belong in a subtree where everything must be smaller than its *grandparent*.

The condition is not local. Every node lives inside an **open interval** fixed by the
ancestors above it. Descend, and carry that interval down:

- going left, the node you came from becomes the new upper bound;
- going right, it becomes the new lower bound.

```python
def is_valid_bst(tree):
    def valid(index, low, high):
        if index >= len(tree) or tree[index] is None:
            return True
        value = tree[index]
        if low is not None and value <= low:
            return False
        if high is not None and value >= high:
            return False
        return valid(2 * index + 1, low, value) and valid(2 * index + 2, value, high)

    return valid(0, None, None)
```

**Use `<=` and `>=`, not `<` and `>`.** The ordering is strict, so a duplicate value
must be rejected. Getting this backwards is the second most common bug here.

**Use `None` for "no bound yet", not a sentinel like `-10**9`.** A real node value
could equal any sentinel you pick, and then a valid tree is rejected.

## An alternative worth knowing

A binary search tree is exactly a tree whose in-order traversal is strictly
increasing. So you can walk in-order and check each value against the previous one.
Same cost, and it reuses the traversal from the previous Problem — but you must
compare against the *previous value*, not sort the output, or you would accept every
tree.

## Cost

O(n) time, O(h) space for the call stack.
