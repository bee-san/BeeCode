## The insight

The only decision at each step is where the next piece ends. Try every possibility,
keep the palindromic ones, recurse on the remainder:

```python
def build(start):
    if start == len(text):
        found.append(list(pieces))
        return
    for end in range(start, len(text)):
        if is_palindrome(start, end):
            pieces.append(text[start:end + 1])
            build(end + 1)
            pieces.pop()
```

The palindrome test is the pruning. A non-palindromic prefix is abandoned immediately,
so its entire subtree — every splitting that would have begun with it — is never
explored. Without it this generates all `2^(n-1)` splittings and filters at the end,
which is the same answer for exponentially more work.

Reaching `start == len(text)` means the whole string has been consumed by valid pieces,
so `pieces` is a complete answer. Record a copy.

## Checking palindromes cheaply

Written as above, each check is O(n) and the total is O(n * 2^n). Two ways to improve
it:

- **Precompute a table.** `is_palindrome[i][j]` for every pair, filled by the recurrence
  `text[i] == text[j] and is_palindrome[i+1][j-1]`, in O(n^2) up front. Then each check
  is O(1). This is the same table that
  [Longest Palindromic Substring](longest-palindromic-substring) builds.
- **Expand around centres.** O(n^2) to find every palindromic substring, then look them
  up.

Neither changes the exponential output size — there can genuinely be exponentially many
splittings, as `"aaaa"` shows with 8 — but they remove the repeated re-checking of the
same substring.

## Pitfalls

**Off by one on the slice.** `text[start:end + 1]` for an inclusive `end`. A missing
`+ 1` drops the last character of every piece and the concatenation no longer
reconstructs `text`.

**Appending `pieces` itself.** Every recorded splitting becomes the same list, empty at
the end.

**Recursing from `end` instead of `end + 1`.** Repeats the last character in the next
piece, so the result is longer than the input.

**Filtering at the end.** Correct and exponentially slower; the pruning is the lesson.

## Cost

O(n * 2^n) as written, dominated by producing the output. O(n) recursion depth.
