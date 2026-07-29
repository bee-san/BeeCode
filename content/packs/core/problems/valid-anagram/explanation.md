## The insight

An anagram is not about order at all — it is about **multiplicity**. Two strings are
anagrams exactly when the multiset of their characters is equal, so the only thing
you need from each string is a tally: character to count.

That reframing kills the temptation to think about permutations. You are not
searching for a rearrangement; you are comparing two histograms.

## Tally and cancel

```python
def is_anagram(s, t):
    if len(s) != len(t):
        return False

    counts = {}
    for character in s:
        counts[character] = counts.get(character, 0) + 1
    for character in t:
        if character not in counts:
            return False
        counts[character] -= 1
        if counts[character] == 0:
            del counts[character]
    return not counts
```

Build the tally from `s`, then spend it down with `t`. If `t` ever asks for a
character the tally cannot supply, the answer is `False`; if the tally is empty at
the end, everything matched exactly.

In real Python you would write `Counter(s) == Counter(t)` and be done, and that is
the right answer in an interview too — say it, then show you can build it by hand,
because "I know the library call" and "I know why it works" are different claims.

Two mistakes to avoid:

**Using a set instead of a tally.** `set("a") == set("aa")` is `True`, so a set-based
solution reports that `"a"` and `"aa"` are anagrams. Sets discard counts, and counts
are the entire content of this problem. The `aabb` / `abbb` case is the same trap in
a form that also has equal lengths, so nothing but real counting saves you.

**Skipping the length check.** It is not merely an optimisation. Without it, the
cancel loop can leave leftover positive counts that you then have to reason about;
with it, "same length plus nothing left over" is airtight, and you reject
mismatched inputs in O(1).

Also resist normalising case. The statement says comparison is case-sensitive, so
calling `.lower()` is a silent behaviour change, not a cleanup.

## Cost

O(n) time and O(k) space, where `k` is the number of distinct characters — at most 52
here, so effectively constant.

Sorting both strings is also correct and is a fine answer, but it costs O(n log n)
time. The tally trades a small dictionary for a linear scan.
