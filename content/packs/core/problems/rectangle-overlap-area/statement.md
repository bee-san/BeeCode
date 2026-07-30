Two axis-aligned rectangles are given as `[x1, y1, x2, y2]`, where `(x1, y1)` is
the bottom-left corner and `(x2, y2)` the top-right. Return the area they overlap.

Rectangles that merely touch — sharing an edge or a corner — overlap in zero area.
Return `0` for those, and for rectangles that do not meet at all.

## Constraints

- `-10^6 <= x1 < x2 <= 10^6` and `-10^6 <= y1 < y2 <= 10^6` for both rectangles
- All coordinates are integers, so the answer is an integer

## Follow-up

The two axes do not interact: the horizontal overlap does not depend on the
vertical one. What one-dimensional question are you answering twice, and what is
the sign of its answer when the two ranges miss each other?
