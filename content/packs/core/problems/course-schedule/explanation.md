## The insight

Model the courses as a directed graph: an edge from a prerequisite to the course it
unlocks. You can take every course exactly when this graph is **acyclic**, because a
cycle is a set of courses each waiting on another, forever.

Kahn's algorithm decides it constructively. Track, for each course, how many
prerequisites it still needs — its *in-degree*. Any course needing none is ready now.
Take a ready course, and for each course it unlocks, decrement that course's count; if
it reaches zero, it just became ready.

```python
ready = deque(course for course in range(n) if remaining[course] == 0)
scheduled = 0
while ready:
    course = ready.popleft()
    scheduled += 1
    for unlocked in unlocks[course]:
        remaining[unlocked] -= 1
        if remaining[unlocked] == 0:
            ready.append(unlocked)
return scheduled == n
```

**The count is the answer.** If the loop ends having scheduled all `n` courses, you
built a valid order. If it stalls early, the courses left over all still have unmet
prerequisites — with nothing ready, they must depend on each other in a cycle. So
`scheduled == n` is exactly the acyclicity test; you never look for the cycle
explicitly.

## Getting the edge direction right

The pair is `[course, prerequisite]`, so the *edge* runs from `prerequisite` to
`course` and the in-degree belongs to `course`. Reversing this yields a solution that
is correct on symmetric inputs and wrong on the examples — one of the easiest bugs
here to write and the hardest to spot.

## Why not depth-first search?

You can also colour nodes white/grey/black and look for an edge back into a grey node.
It works and is the same complexity. Kahn's is preferred when you also want the order
itself, and it needs no recursion, which matters at `n = 100,000`.

## Cost

O(n + m) time and O(n + m) space.
