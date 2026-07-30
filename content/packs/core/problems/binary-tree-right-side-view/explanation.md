## The insight

Walk level by level, and from each level keep the last node. Building the levels is
the same loop as in [Level-Order Traversal of a Binary Tree](binary-tree-level-order);
the only change is taking one value instead of all of them.

```python
def right_side_view(tree):
    if not tree or tree[0] is None:
        return []
    view = []
    frontier = [0]
    while frontier:
        view.append(tree[frontier[-1]])
        following = []
        for index in frontier:
            for child in (2 * index + 1, 2 * index + 2):
                if child < len(tree) and tree[child] is not None:
                    following.append(child)
        frontier = following
    return view
```

Because children are appended left before right, and the parents are already in
left-to-right order, each `following` list is in left-to-right order too — so
`frontier[-1]` really is the rightmost node.

## The depth-first version

Visit the right child before the left, and track the depth. The **first** node seen
at any depth is that level's rightmost:

```python
def walk(node, depth):
    if node is None:
        return
    if depth == len(view):
        view.append(node.value)
    walk(node.right, depth + 1)
    walk(node.left, depth + 1)
```

`depth == len(view)` is the whole trick — it is true exactly once per depth, the
first time that depth is reached. O(h) space rather than O(width), which is the
better trade on a wide, shallow tree.

## Pitfalls

**Following right children only.** The Problem's central trap. In
`[1, 2, 3, 4, null, null, null, 8]` the deepest levels contain nothing but
left-descendants, and chasing `node.right` from the root stops after two levels.

**Skipping levels with no right child.** Every non-empty level contributes exactly
one value, even one holding a single left child.

**Visiting left first in the depth-first form and keeping the last.** Also works,
but then the condition has to *overwrite* the entry for that depth rather than only
append, and forgetting that yields the left side view.

## Cost

O(n) time. O(width) space level by level, or O(h) depth-first.
