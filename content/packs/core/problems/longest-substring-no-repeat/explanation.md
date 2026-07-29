## The insight

The brute-force version checks every substring for duplicates: O(n^2) substrings, each
costing O(n) to check. But notice what the checking wastes. If `"abca"` has a repeat,
you already know something about `"abcab"` — the duplicate does not disappear by
extending to the right.

So maintain a **window** `s[start:index]` that is *always* duplicate-free, and walk
`index` forward one character at a time. Two cases:

- the new character is not in the window — extend, and the window grew;
- the new character *is* in the window — move `start` past the previous occurrence,
  which is the minimum move that restores the invariant.

Each index is visited once by `index` and at most once by `start`, so the whole scan is
O(n).

## The loop

```python
def longest_unique_substring(s):
    last_seen = {}
    best = 0
    start = 0
    for index, character in enumerate(s):
        previous = last_seen.get(character)
        if previous is not None and previous >= start:
            start = previous + 1
        last_seen[character] = index
        best = max(best, index - start + 1)
    return best
```

`last_seen` maps a character to the last index it appeared at — anywhere in the string,
not just in the window. That is the subtlety the next section is about.

## The trap

The obvious version of the duplicate case is wrong:

```python
if character in last_seen:
    start = last_seen[character] + 1   # <- wrong
```

Try `"abba"`. At the final `a`, `last_seen['a']` is `0` — from before the window, which
by then starts at index 2. Jumping to `0 + 1 = 1` drags `start` **backwards**, so the
window becomes `s[1:4] = "bba"`, which contains a duplicate. The invariant is broken and
the answer comes out wrong.

The guard `previous >= start` — or equivalently `start = max(start, previous + 1)` —
says "only move the left edge forward". A stale index from outside the window is
information about the string, not about the window, and must be ignored.

This is why `"abba"` is a test rather than a footnote. It is the smallest input that
distinguishes the two versions, and both look correct on `"abcabcbb"`.

## Why this Problem is worth repeating

Sliding window is one of the highest-yield patterns in interviews, and this is its
cleanest non-trivial instance: the window is defined by an invariant, and the whole
algorithm is "restore the invariant with the smallest possible move". Once that framing
is automatic, the variants — longest window with at most *k* distinct characters,
smallest window containing a target set — stop being separate Problems.

The lesson that transfers is narrower and more useful than the code: when you cache
positions, be explicit about whether a cached value is still inside the structure you
are reasoning about.
