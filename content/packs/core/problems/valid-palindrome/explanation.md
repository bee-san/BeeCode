## The insight

You do not need the cleaned string, only the ability to ask for the next
*interesting* character from each end. So walk one index in from the left and one
in from the right, and let each of them skip over the punctuation on its own.

```python
def is_palindrome(s):
    left, right = 0, len(s) - 1
    while left < right:
        if not s[left].isalnum():
            left += 1
        elif not s[right].isalnum():
            right -= 1
        elif s[left].lower() != s[right].lower():
            return False
        else:
            left += 1
            right -= 1
    return True
```

Skipping and comparing are separate branches on purpose: after advancing past a
comma you must re-test the new character, because it might be a comma too.

## Pitfalls

**Skipping only once.** `if not alnum: left += 1` followed immediately by a
comparison misreads `"a,,a"`. Loop until the character is interesting.

**`isalpha()` instead of `isalnum()`.** Digits count. `"0P"` is not a palindrome,
and a letters-only filter reduces it to `"P"` and wrongly says it is.

**Forgetting case.** Lowercase both sides, or `"Aa"` fails.

**`left <= right`.** Harmless — the middle character always equals itself — but
`left < right` says what you mean.

The one-line `cleaned == cleaned[::-1]` version is correct and readable, and worth
writing when space is not the point. This Problem exists to practise the
two-pointer form.

## Cost

O(n) time; each index moves only inwards, so together they cover the string once.
O(1) extra space.
