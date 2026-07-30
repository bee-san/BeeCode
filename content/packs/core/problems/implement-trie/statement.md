Implement a prefix tree supporting three operations:

- `insert(word)` — add a word to the tree.
- `search(word)` — is that exact word present?
- `starts_with(prefix)` — is any inserted word prefixed by this?

The distinction between the last two is the point. After inserting `"apple"`,
`search("app")` is `False` but `starts_with("app")` is `True`.

Because BeeCode tests functions rather than classes, replay a list of operations. Each
is a `[name, argument]` pair, where `name` is `"insert"`, `"search"` or
`"starts_with"`.

Return a list holding the result of each `search` and `starts_with`, in order.
Inserts produce nothing.

## Constraints

- `1 <= len(operations) <= 30_000`
- Words and prefixes are non-empty, lowercase `a`-`z`, at most 2000 characters.
- The same word may be inserted more than once.

## Follow-up

A set of words answers `search` in O(1) and cannot answer `starts_with` without
examining every word. The tree exists for the prefix query — and a single boolean per
node is all that distinguishes "a word ends here" from "the path continues".
