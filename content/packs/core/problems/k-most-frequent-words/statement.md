Return the `count` most frequent words in `words`, most frequent first. Words with the same frequency
appear in **lexicographic** order.

## Constraints

- `1 <= len(words) <= 50000`
- `1 <= len(words[i]) <= 20`
- Words are lowercase letters only.
- `1 <= count <= number of distinct words`

## Follow-up

Tallying then sorting is O(d log d) in the number of distinct words. A heap of size `count` gets it
to O(d log count) — but the comparison has to order by frequency *descending* and by spelling
*ascending* at once, which is where a min-heap gets awkward. What is the cleanest way to express a
mixed-direction ordering?
