## The insight

Store the words in a prefix tree exactly as in
[Implement a Prefix Tree](implement-trie). The search is where the wildcard changes
things: it stops being a walk and becomes a **backtracking search over the tree**.

At each position in the pattern:

- an ordinary character — descend into that one child, or fail if it is absent
- a `.` — try *every* child, and succeed if any of them leads to a match

```python
def matches(node, pattern, position):
    if position == len(pattern):
        return node.is_word
    character = pattern[position]
    if character == ".":
        return any(matches(child, pattern, position + 1)
                   for child in node.children.values())
    child = node.children.get(character)
    return child is not None and matches(child, pattern, position + 1)
```

The base case is the same subtlety as in the plain prefix tree: reaching the end of
the pattern is not enough, the node must be marked as a word. Otherwise `"b.."`
matches a stored `"bxxx"` prefix that is not a word.

## Why the tree still helps

The wildcard search can branch, but every non-wildcard character still prunes. A
pattern like `"b.."` explores only the subtree under `b` — no word starting with
another letter is ever touched. Contrast a list of words, where every candidate must
be examined character by character. The worst case, a pattern of nothing but dots,
does visit every node; the common case with a few concrete characters does not.

## Bucketing by length

An easy improvement worth mentioning: since a match requires equal lengths, keep one
tree per word length and search only the one matching `len(pattern)`. Cheap to
implement and it removes a whole class of wasted descent.

## Pitfalls

**Iterating the sentinel.** With the dict-of-dicts encoding, `"$"` is a key of the
node. A `.` that tries every key will try `"$"` too and then index into `True`.
Filter it, or use an explicit node class where the flag is not a child.

**Succeeding on a prefix.** See the base case above.

**Treating `.` as "zero or more".** It matches exactly one character, so lengths must
agree. `"a."` does not match `"a"`.

**Returning from inside the loop unconditionally.** The `.` branch must return `True`
on the first success and only report `False` after *all* children have failed. An
early `return` of the first child's verdict is a common slip.

## Cost

`add` is O(len(word)). `search` is O(len(pattern)) when there are no wildcards, and up
to O(number of nodes) for a pattern of all dots.
