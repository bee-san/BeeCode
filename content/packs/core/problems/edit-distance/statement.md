Return the fewest single-character edits that turn `source` into `target`. The permitted edits
are:

- **insert** a character,
- **delete** a character,
- **replace** a character with another.

## Constraints

- `0 <= len(source), len(target) <= 500`
- Both strings are lowercase `a`-`z`.

## Follow-up

Compare the last characters. If they agree, neither needs touching. If they do not, exactly one
edit is spent, and the three edit kinds correspond to three different smaller problems. Which
smaller problem does each edit leave behind?
