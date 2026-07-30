## The insight

Kahn's algorithm. Count how many prerequisites each course is still waiting on, start
with the courses waiting on none, and each time you take a course, decrement its
dependents:

```python
ready = [label for label in range(n) if outstanding[label] == 0]
order = []
while ready:
    label = ready.pop(0)
    order.append(label)
    for unlocked in unlocks[label]:
        outstanding[unlocked] -= 1
        if outstanding[unlocked] == 0:
            ready.append(unlocked)
```

The order is built as a by-product. [Course Schedule](course-schedule) throws it away and
returns a boolean; here it is the answer.

## The cycle check is a count

If a cycle exists, every course in it permanently waits on another course in it, so none
ever reaches zero and none is ever enqueued. The loop drains and `len(order) < n`. That
single comparison is the whole cycle detection — no colouring, no recursion-stack
bookkeeping.

Note it must be `!= n`, not "is the order non-empty": a graph can have a cycle *and*
several schedulable courses outside it, so a partial order comes back looking healthy.

## Enqueue when it hits zero, not when it drops

Decrementing and testing `== 0` enqueues each course exactly once. Testing `<= 0`, or
enqueueing on every decrement, adds duplicates and the length check then passes on an
invalid order.

## Direction, twice

`[course, prerequisite]` reads backwards relative to the edge you want: the edge runs
*from* the prerequisite *to* the course. So `unlocks[prerequisite].append(course)` and
`outstanding[course] += 1`. Swapping these produces a reversed order that is wrong in a
way most small tests do not catch — which is why a forced chain is in the suite.

## Pitfalls

**`ready.pop(0)` on a list is O(n).** Fine at `n = 2000`; use `collections.deque` and
`popleft` for the real thing. Either end works — this is a set of ready courses, not a
queue with meaning.

**Returning `order` when it is short.** A partial order silently violates prerequisites.

**Forgetting courses with no prerequisites at all.** They must be seeded, or nothing
starts.

## Cost

O(n + e) time and space. The depth-first alternative — post-order with a
currently-visiting marker for cycles — is the same complexity and needs a third state per
node.
