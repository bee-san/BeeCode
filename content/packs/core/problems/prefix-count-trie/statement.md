Given a list of lowercase `words` and a list of `queries`, return a list holding, for
each query, how many words start with that query string.

A word counts as starting with itself. Duplicated words are counted separately.

## Constraints

- `0 <= len(words) <= 20_000` and each word is 1–20 lowercase letters
- `0 <= len(queries) <= 20_000` and each query is 1–20 lowercase letters
- The result must have exactly one entry per query, in the same order.

## Follow-up

Checking every word against every query is `len(words) × len(queries)` string
comparisons — around 400 million on the largest input, which will not finish in time.
A trie answers each query in time proportional to the query's own length, no matter
how many words there are. What do you have to store at each node to make the count
available immediately?
