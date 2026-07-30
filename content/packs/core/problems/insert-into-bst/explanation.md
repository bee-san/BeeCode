## The insight

A search tree already tells you where every value belongs. The comparison that
*finds* a value is the same comparison that decides where to *put* it, so
insertion is a search that ran off the end of the tree.

Start at the root. If the value is smaller, it belongs somewhere in the left
subtree; if larger, the right. Follow that decision down until the slot you want
is empty — and that empty slot is the answer, because it is the only place a leaf
can go without breaking the ordering of anything above it.

## In the level-order encoding

The tree is a list, not nodes, so "go left" is arithmetic: from index `i` the
children are `2i + 1` and `2i + 2`.

```python
def insert_into_bst(tree, value):
    nodes = list(tree)
    if not nodes or nodes[0] is None:
        return [value]

    index = 0
    while True:
        child = 2 * index + 1 if value < nodes[index] else 2 * index + 2
        while len(nodes) <= child:
            nodes.append(None)
        if nodes[child] is None:
            nodes[child] = value
            break
        index = child

    while nodes and nodes[-1] is None:
        nodes.pop()
    return nodes
```

Three details are easy to get wrong:

**The empty tree is not a special case of the walk.** There is no root to compare
against, so it has to be answered before the loop starts.

**Padding is mandatory before writing.** The slot you are aiming at may be past
the end of the list — index 2 in a one-element tree — and `nodes[child] = value`
would raise rather than grow.

**Trailing `None`s must go.** The encoding omits them, so padding out to the slot
and then leaving the padding behind produces a list that means the same tree but
does not equal the expected one.

## Cost

O(h) comparisons, where `h` is the height: `log n` on a balanced tree, `n` on a
chain.

The list itself is the catch. Level-order indexing reserves a slot for every
position a node *could* occupy, so a chain of `h` nodes needs a list of length
`2^h`. That is fine here because the trees are small, and it is exactly why real
implementations hold nodes and pointers rather than an array.
