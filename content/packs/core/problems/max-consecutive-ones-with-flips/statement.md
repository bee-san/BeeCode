`bits` holds only `0`s and `1`s. You may flip at most `budget` of the zeroes to ones. Return the
length of the longest run of consecutive ones you can achieve.

## Constraints

- `1 <= len(bits) <= 100000`
- Each entry is `0` or `1`.
- `0 <= budget <= len(bits)`

## Follow-up

"At most `budget` flips" is the same as "a window containing at most `budget` zeroes", so this is a
window that grows while it is legal and shrinks when it is not. Which end moves when the window
becomes illegal, and does the window ever need to shrink by more than one place at a time?
