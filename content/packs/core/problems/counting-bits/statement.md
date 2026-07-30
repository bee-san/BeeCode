Return a list of length `limit + 1` whose entry at index `i` is the number of set bits in `i`.

## Constraints

- `0 <= limit <= 100000`

## Follow-up

Counting each number independently costs O(limit log limit). There is an O(limit) way, because the
answer for `i` is one bit more than the answer for a smaller number you have already computed.
Which smaller number — and is there more than one choice that works?
