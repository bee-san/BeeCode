## The insight

Four cases, in this order:

1. both nodes absent — the same, so `True`
2. exactly one absent — different shapes, so `False`
3. values differ — `False`
4. otherwise recurse on both pairs of children

```python
def same_tree(a, b):
    if a is None and b is None:
        return True
    if a is None or b is None:
        return False
    if a.value != b.value:
        return False
    return same_tree(a.left, b.left) and same_tree(a.right, b.right)
```

The order is what makes it correct. Testing `a.value` before establishing that both
nodes exist is an attribute error on a null; testing "exactly one absent" before
"both absent" reports a difference for two empty subtrees.

## Why the lists cannot just be compared

Because the padding is not canonical here. `[1, 2]` and `[1, 2, null, null]` are the
same tree, so `left == right` is wrong — and it is a shortcut worth naming, because
in a representation where trailing `null`s *were* always trimmed it would actually
work, and it would teach nothing. Reading past the end of a shorter list as `None`
(the `at` helper) makes the padding irrelevant.

## Pitfalls

**Comparing traversals.** Two different trees can share a pre-order value sequence,
so equal traversals do not imply equal trees — unless the traversal also records the
absent children, which is precisely what
[Serialize a Binary Tree](serialize-binary-tree) does.

**Falsy values.** `if not node` treats a node holding `0` as absent. Compare against
`None` explicitly. The suite contains a zero-valued tree for this reason.

**`and` on the two recursions.** Both must hold. Returning after only the left one
misses everything on the right.

## Cost

O(n) time, O(h) space.
