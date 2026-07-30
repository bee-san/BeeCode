## The insight

Breadth-first search visits nodes in order of depth, so the first leaf it meets is the shallowest
one. Return as soon as you find a node with no children:

```python
frontier, depth = [root], 1
while frontier:
    following = []
    for node in frontier:
        if not node.left and not node.right:
            return depth
        following.extend(child for child in (node.left, node.right) if child)
    frontier, depth = following, depth + 1
```

That stops as soon as possible: a shallow leaf on the left means the deep right subtree is never
walked at all.

## Why `min` is the wrong recursion

The obvious mirror of maximum depth,

```python
return 1 + min(min_depth(node.left), min_depth(node.right))
```

is wrong. For a node with only a right child, `min_depth(node.left)` is `0`, so the whole
expression is `1` — claiming the node is a leaf when it is not. `[2, null, 3, null, null, null, 4]` is a chain
of three nodes with one leaf, and the naive recursion answers `1` instead of `3`.

A leaf is a node with **no** children. A node with one child is not a leaf, and there is no
shortest path that stops there.

## The correct recursion

Branch on how many children exist:

```python
if not node:            return 0
if not node.left:       return 1 + min_depth(node.right)
if not node.right:      return 1 + min_depth(node.left)
return 1 + min(min_depth(node.left), min_depth(node.right))
```

Correct, and it always explores the whole tree, unlike the breadth-first version. Both are O(n)
worst case; only breadth-first is better than that on the trees where it matters.

## Where the level-order indices come from

With the tree flattened into a list, node `i`'s children are at `2i + 1` and `2i + 2`. A child is
present when its index is in range and the entry is not `null`. No node objects need constructing —
though building them is equally fine, and is what the recursive version reads best with.

## Pitfalls

**Using `min` unconditionally.** Reports 1 for any root with a single child.

**An empty tree.** `0`, and not an error.

**Counting edges rather than nodes.** A single-node tree has depth 1 here.

**Continuing the sweep after finding a leaf.** Correct but throws away the early exit.

## Cost

O(n) worst case, and much less on unbalanced trees with the breadth-first version. O(width) space
for the frontier.
