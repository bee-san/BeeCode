Given a list of strings `strs`, return the longest string that is a prefix of every
string in the list.

If there is no common prefix, return the empty string `""`. If `strs` is empty,
return `""`.

## Constraints

- `0 <= len(strs) <= 200`
- `0 <= len(strs[i]) <= 200`
- `strs[i]` consists of lowercase English letters.
- Matching is case-sensitive and the empty string is a prefix of everything.

## Follow-up

The answer can never be longer than the shortest string in the list. Can you use that
to stop scanning early, and can you find the answer without ever building
intermediate strings?
