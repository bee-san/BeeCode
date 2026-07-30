## The insight

Words that share a prefix should share the work of storing it. A **trie** does that:
one node per distinct prefix, with a child edge per next character. `"app"` and
`"apple"` walk the same three nodes before diverging.

Now the key move for this Problem. Do not store the words at each node and count them
later — store a **counter of how many words pass through this node**, maintained as you
insert. Then a prefix query is: walk the query's characters, and read the counter. No
subtree traversal, no per-query work beyond the query's own length.

```python
for word in words:
    node = root
    node["passing"] += 1
    for character in word:
        node = node["next"].setdefault(character, {"passing": 0, "next": {}})
        node["passing"] += 1
```

And the query:

```python
for character in query:
    node = node["next"].get(character)
    if node is None:
        break                     # no word has this prefix
results.append(0 if node is None else node["passing"])
```

## The details that matter

**Increment on the way through, including the root.** The root's counter is the total
word count, which is the right answer for the empty prefix. Incrementing only at the
final node of each word would count *exact matches*, a different question.

**Break out on a missing child, and report 0.** A query whose path leaves the trie has
no matching words. Continuing to index into `None` raises instead of answering.

**"Passes through" is not "ends here".** If you also needed exact-match counts you
would keep a second counter per node. They are different numbers and conflating them
makes `"app"` in the example answer `1` instead of `2`.

## Cost

Building is O(total characters in words). Each query is O(len(query)), independent of
the number of words — which is the entire reason to build the trie rather than compare
every pair.
