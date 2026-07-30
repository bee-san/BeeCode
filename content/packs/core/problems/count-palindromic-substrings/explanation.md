## The insight

Expand around all `2n - 1` centres, and count one palindrome per successful widening step:

```python
def widen(low, high):
    found = 0
    while low >= 0 and high < len(text) and text[low] == text[high]:
        found += 1
        low -= 1
        high += 1
    return found
```

Each iteration confirms a distinct palindrome — the one spanning `low..high` at that moment
— so the loop counter *is* the answer for that centre. No slicing, no set, no second pass.

## Why every centre is counted separately

The question counts occurrences, not distinct strings, so `"aaa"` gives 6 rather than 3.
That makes the count strictly simpler: no deduplication, and the sum over centres is
exactly right, because every palindromic substring has exactly one centre and is therefore
counted exactly once.

That last sentence is the correctness argument, and it is worth stating — it is why summing
over centres cannot double count.

## Both centre kinds

`widen(centre, centre)` for odd lengths, `widen(centre, centre + 1)` for even. The even
call starts with `low` and `high` adjacent, so it counts nothing when the two characters
differ, which is exactly right.

The minimum answer for a string of length `n` is `n`, from the single characters — a useful
sanity check, since it means every odd centre contributes at least one.

## Pitfalls

**Counting distinct strings.** `"aaa"` becomes 3 instead of 6.

**Only odd centres.** Undercounts every even-length palindrome; `"aa"` gives 2 instead of 3.

**Slicing and calling a palindrome check per substring.** O(n^3) and it re-derives what
expansion already knows.

**Counting the centre itself outside the loop as well.** Double counts the single
characters.

## Cost

O(n^2) time, O(1) space. Manacher's algorithm gives O(n) here too.
