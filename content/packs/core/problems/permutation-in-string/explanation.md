## The insight

A rearrangement of `needle` is precisely a string with `needle`'s letter counts.
Order is irrelevant, so the question is about a **fixed-width window** and its
tally.

Build the tally of the first window, compare, then slide: one character enters on
the right, one leaves on the left, two updates per step.

```python
def contains_permutation(haystack, needle):
    width = len(needle)
    if width > len(haystack):
        return False
    wanted = Counter(needle)
    window = Counter(haystack[:width])
    if window == wanted:
        return True
    for right in range(width, len(haystack)):
        window[haystack[right]] += 1
        leaving = haystack[right - width]
        window[leaving] -= 1
        if window[leaving] == 0:
            del window[leaving]
        if window == wanted:
            return True
    return False
```

## Deleting the zeros matters

This is the bug worth internalising. `{"a": 1, "b": 0}` and `{"a": 1}` describe
the same window, but as dictionaries they are not equal. If you leave exhausted
letters behind at zero, the comparison fails on windows that are in fact correct.
Either delete the key when it hits zero, or compare with a method that ignores
zero entries.

Python's `Counter` does not help you here: `Counter(a=1, b=0) == Counter(a=1)` is
`False`.

## Alternatives

Keep a single integer `matched`, the number of distinct letters whose counts
currently agree, and adjust it as characters enter and leave. Then each step is
O(1) instead of O(26). Faster, and considerably easier to get subtly wrong.

Sorting each window is O(n * w log w) and will time out on the long cases.

## Pitfalls

**Forgetting the length guard.** `haystack[:width]` silently truncates when
`needle` is longer, and you compare a short window against a long tally.

**Growing the window instead of sliding it.** The width is fixed by `needle`.

## Cost

O(n * 26) = O(n) time with dictionary comparison, O(1) space.
