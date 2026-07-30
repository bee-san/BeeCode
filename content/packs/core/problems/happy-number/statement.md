Repeatedly replace a positive integer by the sum of the squares of its digits. Return `True` if
this eventually reaches `1`, and `False` if it loops forever without doing so.

## Constraints

- `1 <= number <= 2^31 - 1`

## Follow-up

Every starting number either reaches `1` or enters a cycle — the sequence can never grow without
bound, because a large number's digit-square sum is much smaller than the number itself. So this
is cycle detection, and it can be done with a set or with two pointers. What plays the role of
"next node" here?
