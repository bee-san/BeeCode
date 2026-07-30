## The insight

Two phases, alternating. Extend the right edge until the window is valid, then
pull the left edge in as far as it will go while validity holds. Record the window
each time it is valid and shorter than the best so far.

Every position is visited at most once by each edge, so despite the nested loop
this is linear.

## Tracking validity in O(1)

Comparing tallies every step is correct but wasteful. Instead count how many
*distinct required characters* are not yet satisfied:

```python
required = Counter(needle)
missing = len(required)
```

`missing` drops by one only at the exact moment a character's count reaches its
requirement — `window[c] == required[c]`, not `>=`. Using `>=` decrements again on
every surplus copy, `missing` goes negative, and the window is declared valid far
too early.

Symmetrically, when the left edge removes a character whose count is currently
*exactly* the requirement, that requirement is about to break, so `missing` goes
back up.

```python
for right, entering in enumerate(haystack):
    window[entering] += 1
    if entering in required and window[entering] == required[entering]:
        missing -= 1
    while missing == 0:
        if right - left + 1 < best_length:
            best_length, best_start = right - left + 1, left
        leaving = haystack[left]
        if leaving in required and window[leaving] == required[leaving]:
            missing += 1
        window[leaving] -= 1
        left += 1
```

## Pitfalls

**Recording after shrinking.** The window must be measured while it is still
valid — at the top of the `while`, before the left character is removed.

**Strict `<` for the best length.** Using `<=` would replace an equally short
earlier window with a later one, breaking the earliest-on-tie rule.

**`missing` as a character count rather than a distinct-character count.** Both
formulations work, but they need different comparisons; mixing them is the usual
source of an off-by-one here.

**No window found.** Initialise `best_length` above `len(haystack)` and check it
at the end, or you will slice out a spurious answer.

## Cost

O(n + m) time, O(alphabet) space.
