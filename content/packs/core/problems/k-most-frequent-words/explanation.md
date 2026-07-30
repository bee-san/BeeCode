## The insight

Tally, then sort by a key that encodes both rules at once:

```python
ordered = sorted(tally, key=lambda word: (-tally[word], word))
return ordered[:count]
```

The tuple `(-frequency, word)` sorts ascending on both components, and negating the frequency turns
"most frequent first" into an ascending comparison. That is the cleanest way to express a
mixed-direction ordering: flip the sign of the field that runs the other way, rather than writing a
custom comparator.

Negation only works on numbers — for a descending *string* field you would need a real comparator,
or to reverse the whole sort and negate the other field instead.

## Why not simply sort by frequency and reverse

Reversing a sorted list reverses *both* components, so the lexicographic tie-break comes out
backwards: `["b", "a"]`, each appearing once, would return `["b", "a"]` instead of `["a", "b"]`.
This is the mistake the second example exists to catch, and it needs a tie to show up at all.

## The heap version

Push `(frequency, word)` into a min-heap of size `count`, popping the smallest whenever it grows too
large. The trouble is the tie-break: among equal frequencies you want to *discard* the
lexicographically largest, so the heap must order words in reverse. With tuples that means storing a
reversed-string key or a wrapper type — Python cannot negate a string.

O(d log count) rather than O(d log d), and worth it only when `count` is much smaller than the
number of distinct words. The sort is the right default.

## Pitfalls

**Sorting by frequency and reversing.** Breaks ties backwards.

**Using a `Counter` and `most_common`.** It orders ties by insertion, not spelling, so it is wrong
here and correct-looking on most inputs.

**Comparing tuples with mismatched types.** `(-frequency, word)` is fine; mixing numbers and
strings in one position is not.

**Assuming `count` is at most the number of distinct words.** It is, by the constraints — worth
noting because the slice would silently return fewer otherwise.

## Cost

O(n + d log d) time with the sort, O(d) space.
