## The insight

Two walkers, one taking a single step per turn and one taking two.

If the walk ends, the fast walker reaches `-1` first and you answer `False`. If the
walk cycles, both walkers are eventually inside the cycle forever — and then the fast
one gains exactly one position on the slow one every turn. A gap that shrinks by one
each turn must reach zero, so they land on the same position. They cannot pass without
meeting, which is the part worth being convinced by rather than told.

```python
slow = fast = start
while True:
    if fast == -1:
        return False
    fast = successors[fast]
    if fast == -1:
        return False
    fast = successors[fast]
    slow = successors[slow]
    if slow == fast:
        return True
```

## The details

**Check for `-1` between the fast walker's two steps.** The fast walker is the only one
that can run off the end, and it can do so on either of its two steps. One check before
the pair is not enough — that is how this becomes an `IndexError` on a list of even
length.

**Compare after moving both, not before.** They start equal. Testing first returns
`True` immediately for every input.

## The alternative

A `set` of visited positions is simpler to write and obviously correct, at O(n) space.
Floyd's is worth knowing because it is O(1) space, and because the same two-pointer
setup finds the *start* of the cycle and the midpoint of a list — the technique
generalises further than this one question.

## Cost

O(n) time and O(1) extra space.
