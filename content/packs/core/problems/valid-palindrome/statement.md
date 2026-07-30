Return `True` if the string `s` reads the same forwards and backwards once you

- ignore every character that is not a letter or a digit, and
- treat upper and lower case as equal.

`"A man, a plan, a canal: Panama"` is a palindrome. `"race a car"` is not.

A string with no letters or digits at all is a palindrome.

## Constraints

- `0 <= len(s) <= 200_000`
- `s` may contain any printable ASCII characters.

## Follow-up

Building a cleaned copy of the string is the obvious approach and costs O(n)
extra space. Can you decide the question with O(1) extra space?
