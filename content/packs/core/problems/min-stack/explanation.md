## The insight

A single `minimum` variable cannot survive a pop. When you remove the smallest
element you need the *previous* smallest, and that is history you threw away.

So store the history. Keep a second stack that, at every depth, holds the minimum
of the main stack up to that depth:

```
values:  -2   0  -3
minima:  -2  -2  -3
```

Push both, pop both. `minima[-1]` is always the answer, and both stacks always
have the same height, so they stay in step by construction.

```python
def run_operations(operations):
    values, minima, answers = [], [], []
    for name, argument in operations:
        if name == "push":
            values.append(argument)
            minima.append(min(argument, minima[-1]) if minima else argument)
        elif name == "pop":
            values.pop()
            minima.pop()
        elif name == "top":
            answers.append(values[-1])
        elif name == "min":
            answers.append(minima[-1])
    return answers
```

## Duplicates are where this breaks

An appealing optimisation is to push onto `minima` only when the new value is
strictly smaller than the current minimum. It halves the space in the common case
and it is wrong unless you also relax the pop: with `[5, 5]` you push 5 onto
`minima` once, pop it on the first pop, and then report the minimum of a stack
that still contains a 5 as if the 5 were gone.

Two fixes work: push on `<=` rather than `<`, or store `(value, count)` pairs.
Pushing unconditionally, as above, is simplest and always right.

## Pitfalls

**`min()` on the whole stack.** O(n) per query, which is the thing being ruled
out.

**Letting the two stacks drift.** Any pop path that touches one and not the other
desynchronises them permanently.

## Cost

O(1) per operation. O(n) extra space for the second stack — the price of O(1)
minima, and there is no way around it if arbitrary pops must be supported.
