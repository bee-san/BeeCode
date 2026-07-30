`cards` holds the numbers on a hand of cards. Return `True` if the whole hand can be split into
groups of exactly `size` cards, where each group is `size` **consecutive** numbers.

Every card must be used, and groups may repeat.

## Constraints

- `1 <= len(cards) <= 10000`
- `1 <= size <= len(cards)`
- `0 <= cards[i] <= 1000000000`

## Follow-up

The smallest remaining card has no choice about its group: nothing smaller exists to sit below
it, so it must be that group's lowest card. Repeat that observation and the greedy writes
itself. What must you check first, before any grouping at all?
