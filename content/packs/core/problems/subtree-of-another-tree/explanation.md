## The insight

Two recursions, and keeping them separate is the whole trick.

- `identical(a, b)` — the four-case comparison from
  [Are Two Trees the Same](same-tree). Exact, whole-subtree equality.
- `search(node)` — visit every node of the tree, and at each one ask `identical`
  against the root of `sub`.

```python
def is_subtree(root, sub):
    if sub is None:
        return True
    if root is None:
        return False
    if identical(root, sub):
        return True
    return is_subtree(root.left, sub) or is_subtree(root.right, sub)
```

Collapsing the two into one function is the usual bug. A single recursion that
descends both trees together cannot restart the match: as soon as a value disagrees
it has to try `sub` against the *child*, from `sub`'s root again — which is exactly
what the outer `search` provides.

## Why "all the descendants" bites

The example pair differs only in one extra leaf hanging below the tree's node `2`.
The values and the shape of the first three levels match perfectly, so a comparison
that stops when `sub` runs out reports a match. `identical` refuses because when
`sub` has `None` where the tree has a node, "exactly one absent" fires.

That is the same reason a pre-order serialisation must include markers for absent
children before substring search is valid. Without them, `[1, 2]` and
`[1, null, 2]` serialise identically and every containment answer becomes
unreliable. With them, `serialize(sub) in serialize(tree)` works in O(n + m) — and
the delimiter matters too, or the value `12` matches inside `123`.

## Pitfalls

**Matching only at the root.** `sub` may sit anywhere, including deep inside.

**Stopping at the first partial match.** A value can match in several places and
only one of them extend to a full subtree, so a failed `identical` must not abort
the search.

**The empty `sub`.** Vacuously present, including in an empty tree. Decide it before
anything else.

## Cost

O(n * m) time in the worst case, O(h) space. O(n + m) with the serialisation trick.
