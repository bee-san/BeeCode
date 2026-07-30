Design a dictionary supporting two operations:

- `add(word)` — store a word.
- `search(pattern)` — does any stored word match? A `.` in the pattern matches any
  single character. Every other character must match exactly, and the lengths must be
  equal.

Because BeeCode tests functions rather than classes, replay a list of operations. Each
is a `[name, argument]` pair, where `name` is `"add"` or `"search"`.

Return a list holding the result of each `search`, in order.

## Constraints

- `1 <= len(operations) <= 10_000`
- Words are non-empty, lowercase `a`-`z`, at most 25 characters.
- Patterns are non-empty and made of lowercase letters and `.`, at most 25 characters.

## Follow-up

Without wildcards this is [a prefix tree](implement-trie) and the search is a single
walk down. A `.` breaks that, because there is no longer one child to descend into.
What does the search become instead, and what stops it from being a full scan of every
stored word?
