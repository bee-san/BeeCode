## The insight

An in-order walk of a binary search tree yields its values in ascending order. So
count nodes as they are visited and return the `k`th:

```python
def kth_smallest(root, k):
    remaining = k
    answer = None
    def walk(node):
        nonlocal remaining, answer
        if node is None or remaining == 0:
            return
        walk(node.left)
        if remaining == 0:
            return
        remaining -= 1
        if remaining == 0:
            answer = node.value
            return
        walk(node.right)
    walk(root)
    return answer
```

The two `remaining == 0` checks are the early exit. Without them the walk still gets
the right answer but visits all `n` nodes; with them it stops after `k`, giving
O(h + k).

## The iterative form is the better answer

An explicit stack makes "stop after `k`" natural rather than bolted on, because the
loop simply ends:

```python
stack, node = [], root
while stack or node:
    while node:                 # descend to the smallest unvisited
        stack.append(node)
        node = node.left
    node = stack.pop()
    k -= 1
    if k == 0:
        return node.value
    node = node.right
```

This is also the shape of a BST *iterator*, which is what to reach for if the
question becomes "and now support `next()`".

## If `k` is queried often

Store a subtree size on each node. Then finding the `k`th is a descent: compare `k`
against the left subtree's size to decide whether the answer is left, here, or right,
and recurse. O(h) per query, at the cost of maintaining the counts through inserts
and deletes — the right trade when reads dominate writes.

## Pitfalls

**Sorting all the values.** Correct, and O(n log n) on data that is already ordered
by structure. It answers a different question than the one being asked.

**Off by one on `k`.** `k = 1` is the smallest, not the second smallest. Check
against a single-node tree.

**Pre-order instead of in-order.** Pre-order is not sorted. The visit must sit
*between* the two recursions.

## Cost

O(h + k) time with the early exit, O(h) space.
