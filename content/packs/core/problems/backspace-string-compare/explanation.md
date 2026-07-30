## The insight

A stack. Push each letter; on `#`, pop if anything is there.

```python
def apply(text):
    kept = []
    for character in text:
        if character == "#":
            if kept:
                kept.pop()
        else:
            kept.append(character)
    return kept
```

The `if kept` guard is the whole edge case: a backspace with nothing to delete is a no-op, not an
error.

## The O(1)-space version

Walk both strings from the right. At each step, skip over the characters that a run of backspaces
deletes, then compare the two surviving characters:

```python
def previous(text, index):
    pending = 0
    while index >= 0:
        if text[index] == "#":
            pending += 1
        elif pending:
            pending -= 1
        else:
            return index
        index -= 1
    return -1
```

It works because a backspace only ever affects characters to its *left*, so scanning rightwards
means every deletion is already known by the time you reach a survivor — the direction is what makes
it possible.

The fiddly part is the two pointers running out at different times. Both reaching `-1` means equal;
one reaching `-1` first means unequal; and forgetting to handle "both exhausted" as a success is the
usual bug. The stack version has none of that, at the cost of O(n) space.

## Why comparing lengths first does not help

Two strings of very different lengths can reduce to the same text — `"a"` and
`"aaa##"` both give `"a"` — so nothing can be concluded before applying the backspaces.

## Pitfalls

**Popping from an empty result.** Guard it, or a leading `#` raises.

**Comparing the strings before applying backspaces.** Unrelated question.

**Applying backspaces left to right on the original string in place.** Repeated deletion from the
front of a list is O(n^2), and the indices shift under you.

**Two empty results.** Equal, and `True`.

## Cost

O(n) time; O(n) space with the stack, O(1) with the right-to-left walk.
