Given `start`, `target`, and a list of allowed `words`, transform `start` into `target` by
changing **one letter at a time**, where every intermediate word must appear in `words`.

Return the number of words in the shortest such sequence, counting both ends. Return `0`
if no sequence exists.

`start` need not be in `words`. `target` must be.

## Constraints

- `1 <= len(start) <= 10`
- Every word in `words` has the same length as `start`.
- `1 <= len(words) <= 5000`
- All words are lowercase `a`-`z`. `words` contains no duplicates.

## Follow-up

The words are vertices and a one-letter change is an edge, so this is a shortest path in
an unweighted graph — breadth-first search. Building the edges by comparing every pair of
words is O(len(words)^2 * length). Generating each word's neighbours by trying every
letter in every position is O(length^2 * 26) per word and does not depend on how many
words there are.
