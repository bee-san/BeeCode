## The insight

A collision requires exactly one arrangement: something moving **right** with
something moving **left** immediately after it. Nothing else meets. So process the
asteroids left to right, keeping the survivors on a stack, and a new asteroid only
fights when it is negative and the stack's top is positive.

The fight is not necessarily one round. A surviving leftward asteroid keeps
eating its way back through the stack until it meets something that stops it,
which is why the check is a `while`.

```python
def collide(asteroids):
    survivors = []
    for asteroid in asteroids:
        alive = True
        while alive and asteroid < 0 and survivors and survivors[-1] > 0:
            if survivors[-1] < -asteroid:
                survivors.pop()            # the right-mover loses, keep fighting
            elif survivors[-1] == -asteroid:
                survivors.pop()            # both die
                alive = False
            else:
                alive = False              # the left-mover loses
        if alive:
            survivors.append(asteroid)
    return survivors
```

## The three outcomes

Getting all three right is the Problem:

| Comparison | Effect |
|---|---|
| top smaller | pop it, and the incoming asteroid fights on |
| equal | pop it, and the incoming asteroid dies too |
| top larger | the incoming asteroid dies, the stack is untouched |

The equal case destroys *both*, which is the one people miss — `[8, -8]` is empty,
not `[-8]`.

## Pitfalls

**Only checking the top once.** `[10, 2, -15]` needs two pops before the `-15`
settles; a single comparison leaves the 10.

**Comparing signed values.** `survivors[-1] < asteroid` with `asteroid` negative is
always false. Compare magnitudes: `survivors[-1] < -asteroid`.

**Pushing a left-mover onto a stack of left-movers.** No collision — two negatives
move the same way — and the sign guard covers it.

## Cost

O(n) time; each asteroid is pushed once and popped at most once. O(n) space, which
is the output itself in the case where nothing collides.
