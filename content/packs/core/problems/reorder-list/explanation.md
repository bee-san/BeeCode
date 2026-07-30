## The insight

Split the list in half, reverse the second half, then interleave. That is the
whole algorithm, and it is worth writing this way even though a list lets you index
from the end, because it is what the linked-list version must do.

```python
def reorder(values):
    if len(values) <= 2:
        return list(values)
    middle = (len(values) + 1) // 2
    front, back = values[:middle], values[middle:]
    back.reverse()
    woven = []
    for index in range(len(front)):
        woven.append(front[index])
        if index < len(back):
            woven.append(back[index])
    return woven
```

## Where the halves are cut

`(len + 1) // 2` gives the front the extra element when the length is odd. For
`[1, 2, 3, 4, 5]` that is `front = [1, 2, 3]` and `back = [5, 4]`, and weaving
gives `[1, 5, 2, 4, 3]` — the middle element trailing at the end, exactly as
required.

Cut the other way and the front runs out first, so the guard has to protect the
*front* index instead. Either convention works; mixing them drops an element.

## The linked-list version

With nodes you cannot slice, so:

1. **Find the middle.** A slow pointer moving one node per step and a fast one
   moving two; when the fast one falls off the end, the slow one is at the middle.
2. **Reverse from the middle on.** The standard three-pointer reversal.
3. **Weave.** Take one node from each list in turn, relinking as you go, and
   remember to terminate the final node — a forgotten `next = None` turns the list
   into a cycle, and the test harness hangs rather than failing cleanly.

## Pitfalls

**Lengths 0, 1 and 2.** All are already in the right order. The general code
happens to handle them, but only if the halving does not produce an empty front.

**Mutating the input.** `back.reverse()` here acts on a slice, which is a copy.
Reversing `values` itself would corrupt the front half you still need.

## Cost

O(n) time. O(n) space as written; the linked-list version is O(1), since reversing
in place only relinks existing nodes.
