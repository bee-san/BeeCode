## The insight

Seed the frontier with every gate, then expand level by level. Each level is one step of
distance:

```python
frontier = [every cell holding 0]
distance = 0
while frontier:
    distance += 1
    following = []
    for cell in frontier:
        for neighbour in four_neighbours(cell):
            if in_bounds and rooms[neighbour] == EMPTY:
                rooms[neighbour] = distance
                following.append(neighbour)
    frontier = following
```

Because all the gates start at level zero together, the wave arrives at each room from
whichever gate is closest, and the very first arrival is the answer. No comparison, no
minimum, no second visit.

## Why the grid doubles as the visited set

`rooms[neighbour] == EMPTY` is the whole visited check. A cell that already holds a
distance was reached in an earlier — therefore shorter or equal — level, so it must not be
overwritten. A wall holds `-1` and fails the test too, and so does a gate holding `0`.
Three conditions for the price of one, and no separate set to keep in sync.

## Why one search per gate is worse

It is correct if you take the minimum, but it re-walks the grid once per gate and needs
somewhere to accumulate the minima. Multi-source does it in one pass, and the code is
shorter. The transferable idea: when the question is "distance to the nearest of these",
start from all of them at once.

## Unreachable rooms

Sealed off by walls, they are never enqueued and so keep `2147483647`. That falls out
without a check — a good sign the marker value was chosen well.

## Pitfalls

**Marking on dequeue rather than on enqueue.** A room between two gates enters the
frontier twice, and the second arrival may overwrite a correct smaller distance.

**Incrementing `distance` per cell.** It must advance once per level.

**Overwriting a room that already has a distance.** Wipes out the shortest answer with a
longer one.

**Indexing before bounds-checking.** Negative indices wrap in Python and let the wave
cross from one edge of the grid to the other.

## Cost

O(rows * columns) time and space. This is the same shape as
[Spoiling Oranges](rotting-oranges) — that Problem wants only the final level number,
this one wants every distance.
