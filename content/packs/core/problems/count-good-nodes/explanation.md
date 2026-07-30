## The insight

Whether a node is good depends on one number: the maximum value on the path from the
root to it. Carry that number down as an argument, updating it as you descend.

```python
def count_good_nodes(tree):
    def walk(index, highest):
        if index >= len(tree) or tree[index] is None:
            return 0
        value = tree[index]
        good = 1 if value >= highest else 0
        highest = max(highest, value)
        return good + walk(2 * index + 1, highest) + walk(2 * index + 2, highest)
    if not tree or tree[0] is None:
        return 0
    return walk(0, tree[0])
```

Most tree recursions compute something about a subtree and return it upward. This one
is the other direction — accumulated context flowing down — and recognising which of
the two a problem needs is most of the skill. Some problems need both, as in
[Diameter of a Binary Tree](binary-tree-diameter).

Because `highest` is rebound locally, each branch gets its own copy and the two
subtrees cannot contaminate each other. A shared mutable maximum would leak the left
subtree's values into the right, and the bug hides on trees that happen to increase
leftward.

## Starting value and the comparison

Seed `highest` with the root's own value and compare with `>=`, so the root is good
against itself. The alternative is to seed with negative infinity — but *not* with
`0`, which silently treats every negative node above as no obstacle. That is why the
suite includes an all-negative tree.

`>=` rather than `>` is the definition: the blocker must be *strictly* greater, so a
node tying with the best so far is still good.

## Pitfalls

**Comparing against the parent only.** The constraint is over the whole path. A node
larger than its parent can still be blocked by a grandparent.

**Seeding the maximum with zero.** Reports the root of `[-1, -2, -3]` as bad.

**Sharing the running maximum across siblings.** See above.

## Cost

O(n) time, O(h) space.
