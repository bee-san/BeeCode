## The insight

Two maps, walked in step:

```python
forward, backward = {}, {}
for left, right in zip(first, second):
    if left in forward and forward[left] != right:
        return False
    if right in backward and backward[right] != left:
        return False
    forward[left] = right
    backward[right] = left
return True
```

`forward` enforces "each character of `first` maps to one thing". `backward` enforces "nothing in
`second` is the target of two characters".

## Why one map is not enough

With `forward` alone, `"ab"` against `"aa"` passes: `a` maps to `a`, `b` maps to `a`, no
contradiction seen. But that relabelling is not reversible — it merges two characters — and the
statement forbids it. A relabelling is a bijection on the characters that appear, and a bijection
needs checking in both directions.

`backward` is what makes the relation symmetric: `is_isomorphic(x, y)` and `is_isomorphic(y, x)`
always agree, which is a property worth having and easy to lose.

## The pattern-normalising alternative

Rewrite each string as the sequence of first-occurrence indices — `"egg"` becomes `[0, 1, 1]`,
`"add"` becomes `[0, 1, 1]` — and compare. Two strings are isomorphic exactly when their patterns
match, because the pattern is a canonical name for the equivalence class. One pass each, and it
generalises neatly to comparing many strings at once, where pairwise checking would be quadratic.

## The length check

`zip` stops at the shorter string, so without the explicit length comparison `"ab"` and `"abc"`
would pass. Guarding first is cheaper than remembering that `zip` truncates.

## Pitfalls

**One map instead of two.** Accepts merges.

**Comparing sorted characters or character counts.** `"ab"` and `"ba"` are isomorphic; `"aab"` and
`"abb"` are too — counting alone gets neither reliably, because position matters.

**Forgetting the length check.** `zip` hides it.

**Assuming the alphabet is lowercase letters.** A fixed 26-entry table breaks on other characters.

## Cost

O(n) time, O(alphabet) space.
