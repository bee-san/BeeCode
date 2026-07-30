Return the shortest substring of `haystack` that contains every character of
`needle`, counting **multiplicity**: if `needle` has two `a`s, the window must
have at least two `a`s.

The characters do not have to be contiguous or in order within the window. If no
such substring exists, return `""`. If several are tied for shortest, return the
one that starts earliest.

For `haystack = "ADOBECODEBANC"` and `needle = "ABC"` the answer is `"BANC"`.

## Constraints

- `1 <= len(haystack), len(needle) <= 100_000`
- Both strings may contain any ASCII letters, upper or lower case, and the case
  matters.

## Follow-up

Grow the window until it is valid, then shrink from the left while it stays valid.
Checking validity by comparing whole tallies costs O(alphabet) per step; a single
counter of "how many required characters are still missing" makes it O(1).
