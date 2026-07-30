Return `True` if `pattern` matches the whole of `text`. The pattern supports two special
characters:

- `.` matches any single character.
- `*` means "zero or more of the character immediately before it".

A `*` always follows a matchable character, never another `*` and never the start of the
pattern. The match must cover the entire `text`, not a prefix.

## Constraints

- `0 <= len(text) <= 20`
- `1 <= len(pattern) <= 30`
- `text` is lowercase `a`-`z`; `pattern` is lowercase `a`-`z` plus `.` and `*`.

## Follow-up

Without `*`, this is a straight character-by-character walk. `*` is what makes it hard: the
group it governs may consume nothing, or one character, or many, and only the rest of the
pattern reveals which. What two indices describe your position?
