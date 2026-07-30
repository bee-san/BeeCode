## The insight

Every palindrome has a centre, so try all the centres and grow outwards from each:

```python
def widen(low, high):
    while low >= 0 and high < len(text) and text[low] == text[high]:
        low -= 1
        high += 1
    return low + 1, high - low - 1      # start, length of the widest match
```

Growing stops at the first mismatch or at an edge, and by then `low` and `high` have each
overshot by one — hence the `+ 1` and the `- 1` in the return. Getting that arithmetic
right by deriving it, rather than by adjusting until the tests pass, is most of the work.

## `2n - 1` centres

A palindrome of odd length is centred on a character; one of even length is centred on the
*gap* between two characters. There are `n` characters and `n - 1` gaps, so `2n - 1`
centres, and each must be tried:

```python
for centre in range(len(text)):
    widen(centre, centre)          # odd length
    widen(centre, centre + 1)      # even length
```

Trying only the odd centres is the classic bug: it returns `"b"` for `"cbbd"` instead of
`"bb"`, and every failing case has an even-length answer.

## The earliest on a tie

Scanning centres left to right and updating only on a **strictly** greater length keeps the
earliest winner. Using `>=` would return the last of the tied answers, which is why
`"babad"` distinguishes the two.

## The table alternative

`is_palindrome[i][j] = text[i] == text[j] and is_palindrome[i+1][j-1]`, filled by increasing
length, is the textbook dynamic-programming form: also O(n^2) time but O(n^2) space, against
O(1) for expanding around centres. The table earns its keep when you need to answer many
palindrome questions about the same string — as in
[Partition a String Into Palindromes](palindrome-partitioning) — but for this question,
expansion is strictly better.

Manacher's algorithm does it in O(n). Worth naming; not worth writing under time pressure.

## Pitfalls

**Only odd centres.** As above.

**Returning the length instead of the substring.** Read the return type.

**Off-by-one in the slice.** Derive `start = low + 1` and `length = high - low - 1` from the
loop's exit condition.

**Initialising `best_length` to `0`.** Harmless here, but with a non-empty input the answer
is at least `1`, and starting at `1` makes the single-character case fall out.

## Cost

O(n^2) time, O(1) extra space.
