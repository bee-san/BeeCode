## The insight

Two searches, advanced in step: one over the grid, one over a prefix tree of all the
words.

Build the tree from `words`, storing the whole word at the node where it ends. Then
depth-first search from every cell, and carry the current tree node along with the
current cell. At each step:

- the cell's letter is not a child of the current node — **stop**. No word in the
  entire dictionary continues this way, so an entire family is pruned at once.
- the child exists — descend in both structures. If that node ends a word, record it.

```python
def explore(row, column, node):
    character = board[row][column]
    if character not in node:
        return
    following = node[character]
    if "$" in following:
        found.add(following["$"])
    board[row][column] = None                  # mark visited
    for next_row, next_column in neighbours(row, column):
        if in_bounds and board[next_row][next_column] is not None:
            explore(next_row, next_column, following)
    board[row][column] = character             # undo
```

The saving is real. Given `"aaa"`, `"aab"` and `"aac"`, the shared `"aa"` prefix is
walked once instead of three times, and a grid region with no `a` is rejected once
for all three.

Note that a word is recorded and the search **continues** — a longer word may extend
through the same path.

## Mark, recurse, restore

The no-reuse rule is enforced by overwriting the cell and putting it back on the way
out. Writing into the board avoids allocating a visited set per path, and restoring it
is what makes the *next* starting cell see a clean grid. Forget the restore and later
searches silently see a board full of holes.

## Pitfalls

**Not deduplicating.** The same word can be found by several paths, and from several
starting cells. A set, or pruning the word out of the tree once found.

**Returning in the found order.** The result is sorted; traversal order is not
alphabetical.

**Reusing a cell.** Without the mark, `[["a"]]` matches `"aaa"` by standing still.

**Keeping the words in a list.** Correct, and it is the version that times out: no
pruning, so every word pays for the full grid walk.

Once a word is found, its node can be pruned from the tree — and if that leaves a
childless node, so can its parent. On dictionaries with heavy prefix sharing this
shrinks the search space as it goes.

## Cost

O(rows * columns * 4^L) in the worst case, where `L` is the longest word, but the
prefix tree cuts the constant enormously because most branches die at the first
letter. Space is O(total characters in `words`).
