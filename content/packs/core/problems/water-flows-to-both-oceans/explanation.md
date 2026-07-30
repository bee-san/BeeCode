## The insight

Reverse the question. Instead of "can this cell reach an ocean?", ask "which cells can
reach *this* ocean?" — and answer it by starting **at** the ocean and walking uphill.

Water flows downhill or level, so reversing every step means the traversal may move to a
neighbour whose height is **greater than or equal** to the current cell's:

```python
if heights[next_row][next_column] < heights[row][column]:
    continue                     # cannot have flowed down to here from there
```

Seed the first traversal with every top-row and left-column cell at once, the second
with every bottom-row and right-column cell. Each traversal is one linear sweep, and the
answer is the intersection of the two reached sets.

## Multi-source, and why it is free

Pushing all of an ocean's edge cells before the loop starts is the multi-source pattern:
the traversal expands from all of them at once, so the total work is still O(cells)
rather than O(cells) per source. Nothing about the traversal changes — only the initial
contents of the stack.

## Why it beats the direct approach

The direct reading needs a fresh search per cell — O((rows * columns)^2) with a lot of
repeated work, since neighbouring cells re-derive each other's answers. Reversing turns
it into exactly two traversals. This "search backwards from the destinations" move
generalises: whenever the same question is asked of every cell about a small set of
targets, invert it.

## The `<=` matters twice

Level ground is passable, so flat regions drain both ways. Using strict `<` in the
forward reading, or strict `>` when climbing, silently drops every plateau — and a grid
of one repeated height, where every cell reaches both oceans, is the case that exposes
it.

## Pitfalls

**Walking downhill.** Reversed traversals climb. Easy to write the forward comparison
out of habit.

**Marking on pop instead of on push.** A cell then enters the stack many times; still
correct, but the work is no longer linear.

**One traversal per ocean per cell.** Correct and quadratic.

**A single shared `reached` set.** The two traversals must be independent, or the
intersection is meaningless.

## Cost

O(rows * columns) time and space — two traversals, each visiting each cell at most once.
