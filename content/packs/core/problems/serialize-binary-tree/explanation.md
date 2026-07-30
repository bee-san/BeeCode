## The insight

A pre-order walk that emits a marker for every absent child is **self-delimiting**:
reading it left to right, you always know exactly what comes next.

Writing:

```python
def encode(node):
    if node is None:
        parts.append("#")
        return
    parts.append(str(node.value))
    encode(node.left)
    encode(node.right)
```

Reading is the mirror image, consuming tokens from a single moving cursor:

```python
def decode():
    token = next(tokens)
    if token == "#":
        return None
    node = Node(int(token))
    node.left = decode()
    node.right = decode()
    return node
```

The two functions have the same shape because they are the same traversal — one
writing, one reading. That symmetry is the sign the format is right.

## Why the markers are not optional

Without them, consider a root `1` with a single child `2`. Left child or right child,
the pre-order walk is `1,2` either way. The sequence cannot distinguish them, so no
parser can. The `#` breaks the tie: `1,2,#,#,#` is a left child and `1,#,2,#,#` a
right one.

This is the same requirement that makes traversal-comparison work in
[Subtree of Another Tree](subtree-of-another-tree), and the reason the delimiter has
to be a real separator: without commas, `1,12` and `11,2` both become `112`.

## Level-order would work too

Emit level by level with `#` for absent nodes and read back the same way, using a
queue. It is what this pack's own representation does, and it is easier to eyeball.
Pre-order is usually preferred in an interview because the recursion is shorter and
needs no auxiliary queue.

## Pitfalls

**Negative numbers.** `-1` is not a single character, so any parser that reads one
character at a time breaks. Split on the delimiter.

**Forgetting the trailing markers.** A leaf emits three tokens — its value and two
`#`s — not one. The decoder consumes exactly what the encoder produced, and a
missing marker desynchronises everything after it.

**A shared cursor that is not shared.** The decoder's position must advance globally
across the whole recursion. Passing an integer by value silently rebuilds the wrong
tree instead of failing.

**`if not node` for absence.** A node holding `0` is a node.

## Cost

O(n) time in each direction, O(n) for the string.
