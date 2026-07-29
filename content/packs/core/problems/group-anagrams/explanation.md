## The insight

Do not compare words to each other. **Give every word a fingerprint** that is identical
for anagrams and different for everything else, then let a dictionary do the grouping.

The fingerprint has to depend on which letters a word contains and how many of each, and
on nothing else — in particular not on their order. Sorting the letters produces exactly
that: `"eat"`, `"tea"` and `"ate"` all become `"aet"`, and `"bat"` becomes `"abt"`. Two
words are anagrams if and only if their sorted letters are equal.

That single change converts an O(n²) all-pairs comparison into one pass with a hash map.
It is the same move as Two Sum — replace "compare these two things" with "look this thing
up" — applied to equivalence classes instead of complements.

## Group by key

```python
def group_anagrams(strs):
    groups = {}
    for word in strs:
        key = "".join(sorted(word))
        groups.setdefault(key, []).append(word)

    return sorted(sorted(group) for group in groups.values())
```

`setdefault` creates the bucket on first sight of a key and returns the existing one
afterwards, so there is no "is this the first word in its group?" branch.
`collections.defaultdict(list)` does the same thing and is the more idiomatic choice in
real code.

The final line is the canonical form BeeCode requires: sort inside each group, then sort
the groups. Both sorts are pure presentation — the grouping was already finished — but
without them the answer is unorderable and the judge cannot compare it. State the
convention, then obey it.

Three mistakes to avoid:

**Keying on a set of letters.** `set("aab") == set("abb")` is `{'a', 'b'}`, so a
set-based key merges words that are not anagrams. Anagram identity is about counts, and
sets throw counts away. The `aab` / `abb` test exists to catch precisely this.

**Using a mutable key.** `sorted(word)` returns a *list*, and lists are unhashable — the
dictionary insert raises `TypeError`. Join it into a string, or use
`tuple(sorted(word))`. Either is hashable; a bare list is not.

**Deduplicating the groups.** If you accumulate into a set instead of a list, `"ab"`
appearing twice in the input yields one `"ab"` in the output. The statement says
duplicates survive, and a group is a list of the input words that landed in it, not a set
of distinct spellings.

## A cheaper key

Sorting each word costs O(k log k) for a word of length `k`. Because the alphabet is
fixed at 26 lowercase letters, you can instead build a count vector and use it as the
key:

```python
counts = [0] * 26
for character in word:
    counts[ord(character) - ord("a")] += 1
key = tuple(counts)
```

That is O(k) per word — asymptotically better, and it is the answer to the follow-up. In
practice the sorted-string key usually wins for short words, because `sorted` on a small
string is a fast C loop while the count vector allocates a 26-element tuple per word.
Know both, and know why you would reach for the second: long words, or a fixed small
alphabet you can exploit.

## Cost

O(n · k log k) time with the sorted key, or O(n · k) with the count vector, where `n` is
the number of words and `k` the maximum word length — plus O(m log m) for the final
canonicalising sorts, where `m` is the number of groups.

O(n · k) space: every word is stored once in its bucket, plus one key per group.

The all-pairs alternative is O(n² · k), which at 10,000 words is 100 million anagram
checks rather than 10,000 fingerprint computations.
