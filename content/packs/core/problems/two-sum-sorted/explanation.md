## The insight

Start with the smallest and the largest. Their sum is the widest bet you can
make, and it tells you which way to move:

- sum too small — the only way up is to abandon the smallest element, so `left += 1`
- sum too large — abandon the largest, so `right -= 1`

Each step deletes exactly one element from consideration, and the deletion is
*safe*: if the sum is too small, the left element cannot pair with anything,
because the largest available partner already fell short.

```python
def two_sum_sorted(numbers, target):
    left, right = 0, len(numbers) - 1
    while left < right:
        total = numbers[left] + numbers[right]
        if total == target:
            return [left, right]
        if total < target:
            left += 1
        else:
            right -= 1
    return []
```

That safety argument is the whole Problem. Without sortedness there is no
"largest available partner", which is why the unsorted version needs a hash map.

## Pitfalls

**Moving the wrong pointer.** Swap the two branches and it still terminates,
still looks plausible, and finds nothing. Say the invariant out loud before you
write the comparison.

**`left <= right`.** Allows `left == right`, which uses one element twice.

**Off-by-one on the convention.** This Problem is 0-based; the classic version of
it is 1-based. Read the signature, not your memory.

## Cost

O(n) time — the two indices between them make at most `n` moves. O(1) extra
space.
