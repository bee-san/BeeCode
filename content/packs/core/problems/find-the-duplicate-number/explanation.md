## The insight

Treat the array as a linked list: index `i` links to index `nums[i]`. Since every
value lies in `1..n`, no link ever points at index 0 and no link ever leaves the
array — so a walk starting at index 0 goes forever, which means it must eventually
repeat a position. That is a cycle.

And the cycle's **entry point** is the duplicate. Why: the entry point is the one
index that two different indices link into, and two indices link into the same place
exactly when two positions hold the same value.

## Floyd, in two phases

**Phase one — meet inside the cycle.** Advance `slow` one link per step and `fast`
two. They must meet, because once both are inside the cycle the gap between them
changes by one each step and so passes through zero.

**Phase two — find the entry.** Reset one walker to index 0 and advance both one
step at a time. They meet at the entry point.

```python
def find_duplicate(nums):
    slow = nums[0]
    fast = nums[nums[0]]
    while slow != fast:
        slow = nums[slow]
        fast = nums[nums[fast]]
    finder = 0
    while finder != slow:
        finder = nums[finder]
        slow = nums[slow]
    return finder
```

The reason phase two works is a short piece of arithmetic. Let the distance from
the start to the entry be `a`, and let the meeting point be `b` steps into a cycle
of length `c`. When they meet, `slow` has taken `a + b` steps and `fast` exactly
twice that, so `a + b ≡ 2(a + b) (mod c)`, giving `a + b ≡ 0 (mod c)`. So from the
meeting point, `a` more steps land on the entry — and `a` steps from the start land
there too.

## What the constraints buy

Several easier solutions are ruled out on purpose. A set is O(n) space. Sorting or
negating visited slots modifies the input. Both are correct and both are worth
mentioning; this Problem exists to force the third answer.

Binary search on the *value* — count how many entries are `<= mid`, and if that
exceeds `mid` the duplicate is at or below it — is O(n log n) time and O(1) space
without modifying anything. A good answer, and easier to derive under pressure.

## Pitfalls

**Starting both walkers at the same place.** Then the loop body never runs. Offset
them by one step before the comparison, as above.

**Forgetting phase two.** The meeting point is somewhere in the cycle, not the
entry. It is usually not the duplicate.

## Cost

O(n) time, O(1) space, input untouched.
