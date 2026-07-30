## The insight

Build the string one character at a time and never write a character that could
not possibly lead to a balanced result. Two counts decide that completely:

- you may write `(` while `opened < n` — pairs remain
- you may write `)` while `closed < opened` — there is something open to close

Nothing else can go wrong. Every leaf of this search is a valid answer, so no
filtering is needed at the end.

```python
def generate(n):
    found, current = [], []

    def build(opened, closed):
        if len(current) == 2 * n:
            found.append("".join(current))
            return
        if opened < n:
            current.append("(")
            build(opened + 1, closed)
            current.pop()
        if closed < opened:
            current.append(")")
            build(opened, closed + 1)
            current.pop()

    build(0, 0)
    return found
```

## The shape of backtracking

Three lines, always the same: append the choice, recurse, **undo the choice**.
The `current.pop()` after each recursive call is what makes one shared list
usable across the whole search; forget it and the list grows without bound and
every answer is wrong.

Passing an immutable string down instead — `build(prefix + "(")` — removes the
need to undo and is easier to trust. It allocates a new string per node, which for
`n <= 8` is irrelevant.

## Pitfalls

**`closed < n` instead of `closed < opened`.** Allows `")("`, which has the right
counts and the wrong order.

**Recursing on the length only.** The terminating condition is about length, but
the *legality* conditions are about the counts. Both are needed.

**`n = 0`.** The initial call immediately satisfies `len(current) == 0`, appends
`""` and returns — correct, and worth checking rather than assuming.

## Cost

The number of answers is the nth Catalan number, about `4^n / n^1.5`. The search
visits no dead ends, so the time is O(answers * n) — optimal up to the cost of
writing the output down.
