Given a list of strings `strs`, group the words that are anagrams of each other and
return the groups.

Two words are anagrams when they use exactly the same characters the same number of
times. Every input word belongs to exactly one group, and a word with no anagram partner
forms a group of one.

## Required output form

Grouping is naturally unordered — nothing about the problem says which group comes first,
or in what order words appear inside a group. To make answers checkable, BeeCode requires
a **canonical form**, and your return value must match it exactly:

1. Each group is sorted alphabetically (ascending).
2. The list of groups is sorted alphabetically by group, so effectively by each group's
   first word.

This is a BeeCode convention, not part of the classic problem. Real interviewers accept
any order. Sorting at the end costs one extra line and lets the judge compare your answer
directly, which is a fair trade for an offline tool with no human in the loop.

Duplicate words in the input stay duplicated in the output — if `strs` contains `"ab"`
twice, its group contains `"ab"` twice.

## Constraints

- `0 <= len(strs) <= 10_000`
- `0 <= len(strs[i]) <= 100`
- `strs[i]` consists of lowercase English letters.
- If `strs` is empty, return `[]`.

## Follow-up

Comparing every word against every other word is O(n²) anagram checks. Instead, give each
word a **key** that is identical for anagrams and different for non-anagrams, then group by
that key in one pass. What is the cheapest such key, and can you build one in O(len(word))
rather than O(len(word) · log len(word))?
