Send a list of strings down a channel that can only carry **one** string, then
recover the original list at the other end.

Use this wire format, so that both halves are checkable:

> For each word, write its length in decimal, then a single `#`, then the word
> itself — with no separator between one word's block and the next.

So `["hi", "there"]` becomes `2#hi5#there`.

Implement `round_trip(words)`. It must return a two-element list:

1. the encoded string, and
2. the list recovered by decoding that string.

Write the decoder as a real decoder: it must read the length, jump over exactly
that many characters, and continue. Do not simply return the input.

## Constraints

- `0 <= len(words) <= 200`
- `0 <= len(word) <= 200`
- A word may contain **any** characters, including digits and `#`.
- The empty list encodes to the empty string.

## Follow-up

Why does a plain separator — join on `,`, split on `,` — fail here, no matter
which character you choose as the separator?
