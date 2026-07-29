## The insight

A closing bracket is never free to match *any* earlier opener. It must match the
**most recent unmatched opener**, because anything opened after that one would
still be hanging open across the boundary — which is exactly what "crossed
nesting" means.

So the only question you ever ask is "what did I open last?" Last in, first out.
That is a stack, and once you say it that way the algorithm writes itself: push
openers, and when a closer arrives pop one item and check it is the partner.

## The scan

```python
CLOSER_TO_OPENER = {")": "(", "]": "[", "}": "{"}

def is_valid(s):
    stack = []
    for character in s:
        if character in CLOSER_TO_OPENER:
            if not stack or stack.pop() != CLOSER_TO_OPENER[character]:
                return False
        else:
            stack.append(character)
    return not stack
```

Three things are easy to get wrong:

**The final check.** `"(["` never fails inside the loop — nothing ever mismatches,
because nothing ever closes. Returning `True` at the end is wrong; you must return
`not stack`. Leftover openers are a failure, and this is the single most common bug
in this Problem.

**Popping an empty stack.** For `"]"` the stack is empty when the closer arrives.
`stack.pop()` raises `IndexError`. The `not stack or ...` guard short-circuits
before the pop, turning a crash into the correct `False`.

**Counting instead of stacking.** Keeping three counters, one per bracket type,
looks tempting and is wrong: `"([)]"` has balanced counts for both types. Counters
throw away order, and order is the whole problem.

## Cost

O(n) time — each character is pushed at most once and popped at most once.

O(n) space in the worst case, which is a string of `n` openers like `"((((..."`.
There is no way to do better in general: you genuinely have to remember the
unmatched openers, and there can be `n` of them.
