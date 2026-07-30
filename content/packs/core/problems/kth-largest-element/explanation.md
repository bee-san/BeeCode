## The insight

You do not need the whole order — only one position in it. So keep just the `k`
largest values you have seen, and keep them in a structure that can surrender the
*smallest of those k* cheaply. That is a **min-heap of size k**.

Walk the input. While the heap holds fewer than `k` items, push. After that, the
heap's root is the smallest of your current top `k`: if the incoming value beats it,
the root is no longer in the top `k`, so replace it. When the walk finishes, the root
is the `k`th largest overall.

```python
smallest_k = []
for value in nums:
    if len(smallest_k) < k:
        heapq.heappush(smallest_k, value)
    elif value > smallest_k[0]:
        heapq.heapreplace(smallest_k, value)
return smallest_k[0]
```

**A min-heap, for the k largest.** This inversion is the part that reads backwards
until it clicks. You want cheap access to the *weakest* member of your elite set,
because that is the only one a newcomer has to beat.

**`heapreplace`, not push-then-pop.** Both are correct; `heapreplace` does it in one
sift instead of two, and it cannot transiently grow the heap to `k + 1`.

**Duplicates need no special handling.** Because the comparison is `>` against the
root and equal values simply are not inserted, `[3, 3, 1]` with `k = 2` keeps both 3s
and answers `3`. Deduplicating first would answer `1`, which the statement explicitly
rules out.

## The other two solutions

**Sort and index:** `sorted(nums)[-k]`. One line, O(n log n), and completely fine for
most inputs. Reach for it unless you have a reason not to.

**Quickselect:** partition around a pivot and recurse into only the side containing
the answer. O(n) expected, O(n²) worst case unless you randomise the pivot. It beats
the heap when `k` is near `n / 2`, and it is the reason this Problem is tagged with it.

## Cost

The heap version is O(n log k) time and O(k) space.
