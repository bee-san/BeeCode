## The insight

Hierholzer's algorithm, iteratively. Keep each airport's onward destinations sorted, and
always take the smallest unused one. When an airport has none left, it is finished — pop
it onto the answer:

```python
stack = ["JFK"]
while stack:
    airport = stack[-1]
    if onwards[airport]:
        stack.append(onwards[airport].pop())   # smallest unused ticket
    else:
        route.append(stack.pop())              # stranded: this is the end
route.reverse()
```

The lists are sorted **descending** so that `pop()` from the end yields the smallest
remaining code in O(1).

## Why the answer comes out backwards

The first airport you get stranded at must be the last stop of the whole trip: there is no
ticket out of it, so nothing can follow. Popping it first and reversing at the end puts it
where it belongs. Everything popped after it is likewise finished, in reverse order of
completion — a post-order walk over the edges.

That is the part worth understanding rather than memorising. It is also why greed never
needs undoing: if the greedy walk strands you early, the stranding point is a genuine
endpoint, and the airports still on the stack have unused tickets that will be spliced in
around it.

## Why plain backtracking is worse

Try the smallest ticket, recurse, and on failure undo and try the next. It gives the same
answer, and on adversarial inputs it explores exponentially many dead ends. Hierholzer's
never backtracks. Both are worth being able to describe; only one is worth writing.

## Why a valid trip is guaranteed

The statement promises it, which spares you checking the Eulerian conditions — at most one
airport with one more outgoing ticket than incoming, and so on. Worth saying aloud that you
are relying on the promise.

## Pitfalls

**Sorting ascending and popping from the end.** Yields the *largest* code, so the
dictionary-order requirement fails.

**Sorting ascending and using `pop(0)`.** Correct but O(n) per removal.

**Forgetting `route.reverse()`.** The trip comes back inside out.

**Indexing `onwards[airport]` for an airport with no outgoing tickets.** A `KeyError` at
the final destination; guard the lookup or fill in empty lists.

**Reusing a ticket.** Removing it from the list on use is what prevents that; a visited set
over airports would be wrong, since airports legitimately repeat.

## Cost

O(e log e) for the sorting, then O(e) for the walk. O(e) space.
