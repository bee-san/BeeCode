## The insight

The digit-square-sum step defines a function, so the sequence is a walk on a functional graph:
each number has exactly one successor. Such a walk always ends either at `1` — a fixed point,
since `1` maps to itself — or in a cycle.

## Why it cannot run away

For a `d`-digit number the sum is at most `81d`, which is far smaller than `10^(d-1)` once `d`
is 4 or more. So the sequence drops quickly into numbers below 1000 and then stays there. A
finite state space with one successor each means a cycle is inevitable if `1` is not reached.

## Two ways to detect the cycle

**A set** of everything seen; stop when a value repeats. O(1) space in practice, since the
reachable set is small, but O(k) in the honest accounting.

**Two pointers** — the same idea as [Find the Duplicate Number](find-the-duplicate-number) and
the linked-list cycle problems. `slow` advances one step, `fast` two; they meet inside the cycle
if there is one, and `fast` reaches `1` if there is not.

```python
slow, fast = number, step(number)
while fast != 1 and slow != fast:
    slow = step(slow)
    fast = step(step(fast))
return fast == 1
```

Genuinely O(1) space, and it is the version worth writing because it needs no bound on how large
the reachable set is.

## The loop condition does both jobs

Checking `fast != 1` before `slow != fast` matters: `1` is a fixed point, so once `fast` reaches
`1` the two pointers would meet there anyway and the meeting test alone cannot tell a happy
number from an unhappy one. Testing for `1` explicitly, and returning `fast == 1`, distinguishes
them.

## Pitfalls

**Only checking for `1` with no cycle detection.** Loops forever on unhappy numbers.

**Starting `slow` and `fast` at the same value.** The loop exits immediately.

**Returning `slow == fast`.** True in both outcomes.

**Forgetting that `1` is happy.** It is already there, in zero steps.

## Cost

O(log n) per step and O(1) space, with the number of steps bounded by the small reachable set.
