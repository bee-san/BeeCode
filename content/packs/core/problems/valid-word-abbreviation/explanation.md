## The insight

Walk both strings together. A letter in the abbreviation must equal the current letter of the word;
a digit begins a number that says how far to jump.

```python
while abbreviation_at < len(abbreviation):
    character = abbreviation[abbreviation_at]
    if character.isdigit():
        if character == "0":
            return False
        length = 0
        while abbreviation_at < len(abbreviation) and abbreviation[abbreviation_at].isdigit():
            length = length * 10 + int(abbreviation[abbreviation_at])
            abbreviation_at += 1
        word_at += length
    else:
        if word_at >= len(word) or word[word_at] != character:
            return False
        word_at, abbreviation_at = word_at + 1, abbreviation_at + 1
return word_at == len(word)
```

## Why the number must be parsed in full

`"12"` is a skip of twelve, not a skip of one followed by a skip of two. Consuming digits one at a
time gives the wrong jump on any multi-digit length, and `"i12iz4n"` is exactly the case that
catches it. The inner loop is not an optimisation; it is the meaning of the notation.

## Why leading zeroes are rejected

Without the rule, `"01"` and `"1"` would both mean "skip one", so the same word would have two
spellings of the same abbreviation — and `"0"` would mean "skip nothing", which makes
`"a0pple"` a valid abbreviation of `"apple"` and defeats the non-adjacency requirement. Checking the
first digit of each run is enough, since the run is parsed as one number.

## Why the final equality matters

`word_at == len(word)` is doing two jobs: it rejects an abbreviation that runs out early
(`"a"` against `"apple"`) and one whose numbers overshoot the end (`"a10"` against `"apple"`).
Overshooting is not caught anywhere else, because `word_at` is only ever compared against the length
when a *letter* is being matched.

## Pitfalls

**Reading digits one at a time.** Breaks on any length of ten or more.

**Allowing a leading zero.** Admits duplicate spellings and zero-length skips.

**Returning `True` when the abbreviation is exhausted.** The word may not be.

**Skipping past the end without noticing.** The final comparison catches it, but only if it is
`==` and not `<=`.

## Cost

O(len(word) + len(abbreviation)) time, O(1) space.
