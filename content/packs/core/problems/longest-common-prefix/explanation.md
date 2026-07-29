## The insight

Two facts pin this problem down before you write any code.

First, **the answer is a prefix of the shortest string**, so its length is capped by
that string and you never have to look past position `len(shortest) - 1`. Pick the
shortest word as your candidate and you have turned an unbounded search into a
bounded one.

Second, **the answer ends at the first column where the words disagree**. Read the
list as a grid of characters: column 0 is every word's first letter, column 1 every
second letter. Scan columns left to right and stop at the first column that is not
uniform. Everything before it is the answer.

Scanning by column rather than by word is the shift that makes this easy. Comparing
words pairwise and intersecting the results also works, but it does more bookkeeping
for the same answer.

## Column scan

```python
def longest_common_prefix(strs):
    if not strs:
        return ""

    shortest = min(strs, key=len)
    for index, character in enumerate(shortest):
        for word in strs:
            if word[index] != character:
                return shortest[:index]
    return shortest
```

If the loop runs to completion, every column of `shortest` matched everywhere, so
`shortest` itself is the prefix.

Notice how much the two framing facts buy you. The empty-string case needs no special
handling: if any word is `""`, it is the shortest, `enumerate` yields nothing, and the
function returns `""`. Likewise a single-element list returns that element, since
there is nothing to disagree with it.

Three mistakes to avoid:

**Iterating over `strs[0]` instead of the shortest.** For `["interspecies", "inter",
"interstellar"]` the first word is 12 characters long, so `word[index]` walks off the
end of `"inter"` and raises `IndexError`. If you insist on using `strs[0]`, you must
add an explicit `index >= len(word)` guard — using the shortest string removes the
need for one.

**Forgetting the empty-list guard.** `min([])` raises `ValueError`. The problem
declares `""` for empty input, so return it up front.

**Growing the answer with string concatenation.** `answer += character` inside the
inner loop allocates a fresh string every time. Track the index and slice once at the
end.

## Cost

O(n · m) time in the worst case, where `n` is the number of strings and `m` is the
length of the shortest one — that is the size of the grid you scan, and you cannot do
better, since every one of those characters could be part of the answer.

O(1) extra space, ignoring the returned slice: `min` returns a reference, not a copy,
and the single slice at the end is the answer itself.
