## The insight

Any separator you pick can appear inside a word. Join on `,` and `["a,b"]`
decodes to `["a", "b"]`; pick `\x00` and you have only pushed the problem to a
rarer input. There is no character that cannot be data.

The fix is to stop delimiting and start **framing**. Say how long the next word
is, and the decoder never has to search inside it:

```
2#hi5#there
^^         length 2, so take the next 2 characters
```

The `#` here is not a separator between words. It only terminates the *number*,
and a number cannot contain `#`, so that one search is always safe.

## The decoder

```python
def decode(encoded):
    words = []
    cursor = 0
    while cursor < len(encoded):
        marker = encoded.index("#", cursor)
        size = int(encoded[cursor:marker])
        start = marker + 1
        words.append(encoded[start:start + size])
        cursor = start + size
    return words
```

Two details do the work. `index("#", cursor)` searches from the cursor, not from
the beginning, so a `#` inside an earlier word cannot confuse it. And the cursor
advances by `start + size` — over the payload, never through it.

## Pitfalls

**Single-digit lengths.** Writing the length as one character breaks on the
first word of length 10. Decimal plus a terminator handles any length.

**The empty word.** `[""]` encodes to `0#`, and `encoded[start:start + 0]` is
`""`. Slicing gives you this for free; an implementation that special-cases
`size == 0` usually gets it wrong instead.

**`split("#")`.** Tempting and wrong for the same reason every separator is
wrong — the content can contain `#`.

## Cost

O(total characters) both ways, with one pass each and no rescanning.
