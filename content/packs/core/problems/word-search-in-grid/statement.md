`board` is a grid of single characters. Return `True` if `word` can be read along a
path of horizontally or vertically adjacent cells, using no cell more than once.

Diagonal steps are not allowed.

## Constraints

- `1 <= rows, columns <= 6`
- `1 <= len(word) <= 15`
- The board and the word are lowercase `a`-`z`.

## Follow-up

The search starts from every cell whose letter matches the word's first character, and
from there it is a depth-first walk that must be able to **undo** its steps: a path that
fails partway has to release the cells it claimed, or a later path cannot use them. What
is the cheapest way to mark a cell as claimed and unclaim it again?
