## The insight

Try every starting cell, and from each one walk depth-first, matching one character per
step. The essential part is that a failed path must leave the board exactly as it found
it.

```python
def explore(row, column, position):
    if board[row][column] != word[position]:
        return False
    if position == len(word) - 1:
        return True
    held = board[row][column]
    board[row][column] = None                    # claim
    for next_row, next_column in neighbours(row, column):
        if in_bounds and explore(next_row, next_column, position + 1):
            board[row][column] = held            # restore before returning
            return True
    board[row][column] = held                    # release
    return False
```

Overwriting the cell is a cheaper visited-set than allocating one per path: it is O(1)
space, and "is this cell claimed?" is just the letter comparison failing. The cost is
that the restore must happen on **every** exit — including the successful one, or the
board is left corrupted for whatever runs next.

## Why the search must restart per cell

The same letter can appear in many places, and only some of them lead anywhere. In
`[["a","a"],["a","b"]]` looking for `"ab"`, three cells hold `a` and only one is
adjacent to the `b`. Committing to the first match found and giving up is the classic
wrong answer.

## Pitfalls

**Forgetting to unclaim.** The bug shows as a word that exists being reported absent,
because an earlier failed attempt is still holding cells. It is invisible on inputs where
the first path happens to succeed.

**Allowing diagonals.** Four neighbours, not eight.

**Checking bounds after indexing.** Python's negative indices wrap silently, so
`board[-1][0]` is a real cell and the path escapes off the top edge into the bottom row.
Test the bounds before the access.

**Reusing a cell.** Without the claim, `[["a"]]` matches `"aa"` by standing still.

## Cost

O(rows * columns * 4^L) in the worst case for a word of length `L`, though most branches
die on the first character comparison. O(L) space for the recursion.

For many words at once, a prefix tree turns the repeated walks into one — see
[Find Many Words in a Grid](word-search-in-grid-many).
