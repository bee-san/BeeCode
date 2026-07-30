Return `True` if `text` can be split into a sequence of one or more words from
`dictionary`.

The same dictionary word may be used any number of times. Every character of `text` must be
used, in order.

## Constraints

- `1 <= len(text) <= 300`
- `1 <= len(dictionary) <= 1000`
- Words are lowercase `a`-`z`, and the dictionary contains no duplicates.

## Follow-up

Greedily taking the longest matching prefix fails. With
`dictionary = ["car", "ca", "rs"]` and `text = "cars"`, taking `"car"` strands a leftover
`"s"`, while taking the shorter `"ca"` succeeds. So the right choice at one position depends
on what remains afterwards, which is what makes this a dynamic-programming question rather
than a scan.
