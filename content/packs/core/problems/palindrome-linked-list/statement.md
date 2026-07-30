Return whether the values of a chain read the same forwards and backwards.

BeeCode passes test arguments as JSON, so the chain arrives as a plain list of its values in order.
That is an honest simplification, not a disguise: the values are exactly what a walk of the chain
would produce.

## Constraints

- `0 <= len(values) <= 100000`
- `0 <= values[i] <= 9`

## Follow-up

Copying into a list and comparing it against its reverse is O(n) space, and is what the JSON
representation nudges you towards. The linked-list answer is O(1) space: find the middle with two
pointers, reverse the second half, compare, and put it back. Which of those steps is the one people
skip?
