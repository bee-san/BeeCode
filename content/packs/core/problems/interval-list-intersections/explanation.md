## The insight

Two intervals overlap on `[max(starts), min(ends)]`, which is empty exactly when that start exceeds
that end. So compute the candidate, keep it if it is non-empty, and move on:

```python
while left < len(first) and right < len(second):
    begin = max(first[left][0], second[right][0])
    finish = min(first[left][1], second[right][1])
    if begin <= finish:
        found.append([begin, finish])
    if first[left][1] < second[right][1]:
        left += 1
    else:
        right += 1
```

## Which pointer to advance

Advance the one that **ends sooner**. That interval cannot overlap anything later in the other list,
because every remaining interval there starts at or after the current one's start and the current
one is already finished before it. So it is spent, and nothing is lost by dropping it.

Advancing the one that *starts* sooner is the tempting alternative and it is wrong: a long interval
starting early may still overlap several short ones, and advancing past it loses all but the first.
`first = [[0, 100]], second = [[1, 2], [3, 4]]` is enough to show it — the answer has two intervals
and the wrong rule reports one.

## Why ties may go either way

When the two ends are equal, both intervals are spent, so advancing either is fine — the other will
be advanced on the next iteration after producing an empty candidate. Advancing both at once is also
correct and slightly faster; the single `else` branch is simpler and the cost is one wasted
iteration.

## Why the output cannot need sorting

Each iteration produces an interval starting at or after the previous one, since both pointers only
move forwards. So the result is built in order — worth checking rather than reflexively sorting at
the end.

## Zero-length intersections

`[5, 10]` and `[1, 5]` share exactly the point 5, and the statement asks for `[5, 5]`. That is why
the test is `begin <= finish` and not `begin < finish`. Whether a touching pair counts is a
definition, not a discovery, which is why the statement spells it out — but with closed intervals the
answer follows.

## Pitfalls

**Advancing the earlier-starting pointer.** Loses overlaps against a long interval.

**Testing `begin < finish`.** Drops the single-point intersections.

**Sorting or merging the result.** Already sorted and already disjoint.

**An empty input list.** The loop does not run and the answer is `[]`.

## Cost

O(n + m) time, O(size of the answer) space.
