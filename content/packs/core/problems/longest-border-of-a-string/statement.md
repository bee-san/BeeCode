A **border** of a string is a proper prefix that is also a suffix — proper meaning shorter than the
whole string.

Return the length of the longest border of `text`, or `0` if it has none.

## Constraints

- `1 <= len(text) <= 5000`
- Lowercase letters.

## Follow-up

Comparing every prefix against the matching suffix is O(n^2). The prefix-function of the
Knuth-Morris-Pratt algorithm computes all borders in O(n), and its recurrence is the interesting
part: when a character fails to extend the current border, where do you fall back to?
