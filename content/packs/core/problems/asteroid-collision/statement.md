Each entry of `asteroids` is a non-zero integer. Its magnitude is the asteroid's
size and its sign is its direction: positive moves right, negative moves left. All
asteroids move at the same speed, so two moving the same way never meet.

When two asteroids collide, the smaller one is destroyed. If they are the same
size, **both** are destroyed. Asteroids moving in the same direction, or moving
apart, never collide.

Return the list of asteroids that survive, in their original relative order.

## Constraints

- `0 <= len(asteroids) <= 20_000`
- `-10_000 <= asteroids[i] <= 10_000` and `asteroids[i] != 0`

## Follow-up

A collision happens only in one specific arrangement of signs. Name it, and notice
that the surviving asteroid may immediately collide again with whatever is now
behind it.
