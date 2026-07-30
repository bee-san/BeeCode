## The insight

Two pointers walk the chain, one node at a time and two nodes at a time. If the chain ends, the
fast one falls off and there is no cycle. If it loops, the fast one enters the cycle and gains on
the slow one until they coincide.

```python
slow = fast = head
while fast and fast.next:
    slow = slow.next
    fast = fast.next.next
    if slow is fast:
        return True
return False
```

## Why they must meet

Once both are inside the cycle, consider the gap from `fast` to `slow` measured forward around the
cycle. Each step, `fast` advances 2 and `slow` advances 1, so that gap shrinks by exactly 1 per
step. A quantity that decreases by 1 each step and lives in `[0, cycle length)` reaches 0.

It reaches 0 rather than skipping past it precisely because the decrement is 1. A fast pointer
moving three at a time changes the gap by 2 each step and can straddle the slow one on a cycle of
odd length — so the classic proof depends on the speeds differing by exactly one.

## Why the end check is doubled

`fast` takes two steps, so both must be checked: after the first step it may already be at the end,
and dereferencing again would be an error. In the table form that is two `-1` tests, one after each
hop. Testing only once passes on cycles and crashes on odd-length chains that terminate.

## The set version

Keep every visited node; a repeat means a cycle. O(n) time and O(n) space, and it is completely
fine — reach for it first if the two-pointer argument is not to hand. The O(1) space is what the
follow-up is asking for, and the same machinery finds where the cycle starts, as in
[Find the Duplicate Number](find-the-duplicate-number).

## Pitfalls

**Checking only `fast` and not its successor.** Falls off the end on some inputs.

**Comparing values rather than identity.** Two nodes may hold the same value without being the same
node; here the indices are the identity.

**Starting `slow` and `fast` apart.** They may then meet spuriously, or the loop condition needs
rethinking.

**An empty chain.** No cycle.

## Cost

O(n) time, O(1) space.
