## The insight

Start at the root and ask where the two values are relative to the current node.

- **Both smaller** — both live in the left subtree, so the answer is deeper on the
  left. Go left.
- **Both larger** — go right.
- **Anything else** — stop. This node is the answer.

That third case is doing more work than it looks. It covers two situations at once:
the values straddle the node, which makes it the split point; or one of them *is*
the node, which makes it an ancestor of the other. Both mean you cannot descend
further without losing one of them.

```python
def lowest_common_ancestor(root, first, second):
    node = root
    while node:
        if first < node.value and second < node.value:
            node = node.left
        elif first > node.value and second > node.value:
            node = node.right
        else:
            return node.value
```

No recursion, no parent pointers, no visited set — the search tree's ordering is the
only information needed.

## Why the strict comparisons matter

`first < value` must be strict. Written as `<=`, a value equal to the node sends the
walk into the left subtree and past the very node that was the answer. The strictness
is what makes "one value is the node itself" fall into the stopping case instead.

## In a general binary tree

Without ordering you have to search. The recursion returns the node it found, or
null:

- if this node is either target, return it
- otherwise recurse both ways; if both sides return something, this node is the
  split point, so return it; if only one does, pass that up

Same idea, but O(n) and O(h) instead of O(h) and O(1) — which is exactly why this
Problem hands you a search tree.

## Pitfalls

**Assuming `first < second`.** They arrive in either order, so both comparisons in
each branch are needed. Sorting them first is a fine defensive move.

**Descending past the answer.** The most common bug, and it only shows when one
target is the ancestor of the other. The suite tests that case both ways round.

## Cost

O(h) time, O(1) space.
