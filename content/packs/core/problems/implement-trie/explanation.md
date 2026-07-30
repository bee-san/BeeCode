## The insight

One node per distinct prefix, with edges labelled by characters. Words that share a
prefix share the path for it, which is what makes the prefix query cheap: walking
`"app"` lands you on a single node, and everything beneath it is exactly the words
starting with `"app"`.

Each node needs two things: its children, and a flag saying whether a word ends
here.

```python
node = root
for character in word:
    node = node.children.setdefault(character, TrieNode())
node.is_word = True
```

All three operations are the same walk, differing only in what they do when it
finishes:

- the walk fell off the tree — `False` for both queries
- the walk completed — `search` returns the flag, `starts_with` returns `True`

That last line is the whole difference between the two, and the reason a node cannot
just be "present or not".

## Sentinel key or explicit node

Using a dict of dicts with a reserved key for "word ends here" is compact and
idiomatic in Python:

```python
node["$"] = True
```

It works because the alphabet is `a`-`z`, so `"$"` cannot collide with a character.
Say that out loud when you write it — a sentinel that *could* appear in the input is
a latent bug, and the safe version is an explicit `is_word` attribute on a small
class.

## Pitfalls

**Missing the search/starts_with distinction.** Returning the flag for both makes
`starts_with("app")` false after inserting `"apple"`; returning `True` for both makes
`search("app")` true. This is the one thing the Problem is testing.

**A children array of fixed size 26.** Faster and perfectly valid, but it makes the
alphabet assumption structural rather than incidental. Mention the trade.

**Re-inserting a word.** Idempotent — the flag is set again on the same node. Nothing
to guard.

**Storing whole words at nodes.** Then `starts_with` needs a search below the node,
and the tree has bought you nothing.

## Cost

Every operation is O(len(word)), independent of how many words are stored. Space is
O(total characters inserted) in the worst case, less when prefixes are shared.
