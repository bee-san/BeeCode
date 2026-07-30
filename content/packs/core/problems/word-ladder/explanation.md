## The insight

Words are vertices, a one-letter difference is an edge, every edge costs the same — so the
shortest ladder is a breadth-first search:

```python
frontier = [start]
length = 1
while frontier:
    length += 1
    following = []
    for word in frontier:
        for candidate in one_letter_changes(word):
            if candidate in allowed:
                if candidate == target:
                    return length
                allowed.discard(candidate)          # claim it
                following.append(candidate)
    frontier = following
return 0
```

The answer counts words, not steps, so it starts at `1` and increments once per level.

## Generating neighbours instead of finding them

The tempting move is to compare every pair of words up front and build an adjacency list:
O(len(words)^2 * length), which at 5000 words is 25 million comparisons. Generating
instead — for each position, substitute each of the 26 letters and test membership in a
set — costs O(length * 26) per word and is independent of the list size. For short words
and long lists, which is this Problem's shape, generating wins outright.

## Removing from the set is the visited marker

`allowed.discard(candidate)` on enqueue does double duty: it records the visit and shrinks
the search space. Because breadth-first search reaches every word by a shortest route the
first time, a second arrival can never be an improvement, so removal loses nothing.

Do it on enqueue, not on dequeue. Two words in the same level often share a neighbour,
and marking late puts it in the frontier twice.

## The checks before the loop

`target not in allowed` returns `0` immediately — the second example — and it is not
optional, because the search would otherwise walk the whole graph before concluding the
same thing. `start == target` returns `1`. And `start` must be removed from `allowed`, or
the search can wander back to where it began.

## Bidirectional search

Searching from both ends at once and stopping when the frontiers meet explores roughly
`2 * b^(d/2)` words rather than `b^d`. It is the standard follow-up, it halves the
effective depth, and the bookkeeping — alternate to whichever frontier is smaller, keep
two visited sets — is where it goes wrong. Mention it; write the plain version first.

## Pitfalls

**Returning the number of steps.** The answer counts words, so it is one more.

**Not requiring `target` in `words`.** A ladder that ends outside the allowed set is not
a ladder.

**Checking `candidate == target` only when dequeuing.** Still correct, one level of extra
work.

**Skipping `letter == word[position]`.** Regenerates the word itself, which is already
removed from the set — harmless, but wasted work in the innermost loop.

## Cost

O(len(words) * length^2 * 26) time and O(len(words) * length) space.
