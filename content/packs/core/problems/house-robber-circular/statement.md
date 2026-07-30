As in [Non-Adjacent Maximum Sum](house-robber), choose a subset of `values` with the
largest sum and no two adjacent entries — except that the list is now **circular**: the
first and last entries are adjacent to each other.

Return that largest sum.

## Constraints

- `1 <= len(values) <= 100`
- `0 <= values[i] <= 1000`

## Follow-up

The circle adds exactly one constraint, and it is a constraint about two specific entries.
Rather than inventing a circular recurrence, notice that any valid answer either leaves out
the first entry or leaves out the last one — it cannot include both. That turns one circular
problem into two of the linear kind you have already solved.
