Given a list of integers `values`, return the values in reverse order.

Think of `values` as a sequence you have been handed head-first — the classic version of
this exercise reverses a singly linked list, and the skill being trained is the same
one: walk the sequence once, rebuilding it back-to-front, without allocating a second
copy of the data.

BeeCode passes test inputs as JSON, which cannot carry node objects, so the input here
is a plain Python list rather than a chain of nodes. That is an honest simplification, not
a disguise: a list gives you random access that a linked list does not, so if you want the
exercise the original intends, solve it with two indices swapping inward and no slicing.
Returning a correct answer any other way still passes.

## Constraints

- `0 <= len(values) <= 100_000`
- `-10^9 <= values[i] <= 10^9`
- The empty list reverses to the empty list.

## Follow-up

Do it in place with O(1) extra space: swap the ends, then move both ends inward. How many
swaps does a list of odd length need, and what happens to the middle element?
