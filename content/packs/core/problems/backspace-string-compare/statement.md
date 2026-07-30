In both strings, `#` means a backspace: it deletes the character before it, if there is one.

Return whether the two strings are equal once every backspace has been applied.

A backspace at the start of the text deletes nothing.

## Constraints

- `0 <= len(first), len(second) <= 200`
- Both contain lowercase letters and `#`.

## Follow-up

Building both results with a stack is O(n) time and O(n) space. Comparing them from the *back*
gets it to O(1) space, because a backspace only ever affects characters to its left. What makes the
right-to-left walk fiddly?
