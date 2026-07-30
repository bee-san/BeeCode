## The insight

Every element faces one independent yes/no question: am I in this subset? Walk the
elements, and at each one explore both answers.

```python
def explore(index):
    if index == len(nums):
        found.append(list(chosen))
        return
    explore(index + 1)              # leave nums[index] out
    chosen.append(nums[index])      # take it
    explore(index + 1)
    chosen.pop()                    # undo -- this is the backtracking
```

The `pop` is the whole technique. `chosen` is one list reused by every branch, so after
exploring the "take it" branch you must restore it to what the caller handed you.
Forget the `pop` and every subsequent subset carries stale elements.

**Append a copy, not the list itself.** `found.append(chosen)` stores a reference to
the single mutable list, so every entry in your answer ends up being the same object —
and by the time you return, that object is empty. `list(chosen)` snapshots it. This is
the most common bug in every backtracking problem, not just this one.

## The bitmask version

There are `2^n` subsets and `2^n` numbers with `n` bits, and the correspondence is
direct: bit `i` set means "include `nums[i]`".

```python
def subsets(nums):
    found = []
    for mask in range(1 << len(nums)):
        found.append([nums[i] for i in range(len(nums)) if mask >> i & 1])
    return found
```

No recursion, no undo step, nothing to forget. It also makes the count obvious, and it
explains the constraint: at `n = 14` the answer already has 16,384 entries, and the
output — not the algorithm — is what bounds the input size.

## Cost

O(n · 2^n) time and output size. You cannot beat that: producing the answer requires
writing it down.
