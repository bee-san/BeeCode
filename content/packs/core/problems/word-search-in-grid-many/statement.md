`board` is a grid of lowercase letters. A word is **present** when its letters can be
read along a path of horizontally or vertically adjacent cells, using no cell more
than once in that word.

Return every word from `words` that is present, **sorted ascending**.

## Constraints

- `1 <= rows, columns <= 12`
- `1 <= len(words) <= 3 * 10**4`
- Words are non-empty, lowercase `a`-`z`, at most 10 characters, all distinct.
- Different words may reuse the same cells; the no-reuse rule applies within a single
  word.

## Follow-up

Searching for each word separately — [the single-word search](word-search-in-grid),
run `len(words)` times — repeats the same walks over and over, because words sharing a
prefix retrace it. Put the words in a prefix tree and walk the grid **once**, carrying
a position in the tree alongside the position in the grid. Then a cell whose letter
leaves the tree prunes every word through it at the same moment.
