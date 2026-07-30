Maintain a growing collection of points and answer queries about squares.

- `add`, given `[x, y]`, adds that point. The same point may be added more than once, and
  duplicates count as separate points.
- `count`, given `[x, y]`, returns how many **axis-aligned squares** of non-zero area can be
  formed using that query point as one corner and three points from the collection.

BeeCode passes test arguments as JSON, so the operations arrive as a replay: a list of
`[name, [x, y]]` pairs. Return a list holding one result per `count` operation, in order, and
nothing for the `add` operations. That is an honest simplification, not a disguise — the replay
is the same sequence of calls an object would receive.

## Constraints

- `1 <= number of operations <= 5000`
- `0 <= x, y <= 1000`

## Follow-up

An axis-aligned square is determined by two **diagonally opposite** corners, and only when the
horizontal and vertical distances between them are equal. Given the query point, iterate over
candidates for the opposite corner — then the other two corners are forced. How many points do
you multiply together?
