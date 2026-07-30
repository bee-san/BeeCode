## The insight

Let `table[i][j]` be whether the first `i` characters of `text` are matched by the first `j` of
`pattern`. Three cases:

**An ordinary character or `.`** — consume one from each side:

```text
table[i][j] = table[i-1][j-1]  if pattern[j-1] == "." or pattern[j-1] == text[i-1]
```

**A `*`** — two possibilities, and they are `or`ed, not chosen between:

```text
table[i][j] = table[i][j-2]                                  # the group matches nothing
           or (table[i-1][j] if the group matches text[i-1])  # it absorbs one more
```

The `j-2` skips both the `*` and the character it governs. The `table[i-1][j]` keeps `j` fixed
while `i` advances — that is how one `*` absorbs many characters, one at a time, without a
loop.

**Anything else** — `False`.

## The empty-text row is where this is usually lost

`table[0][j]` must be `True` for patterns that can match nothing at all: `"a*"`, `"a*b*"`,
`".*"`. That is `table[0][j] = table[0][j-2]` for every `*`. Omit this row and `"c*a*b"`
against `"aab"` fails, and so does every case where a `*` group matches zero characters at the
start.

`table[0][0] = True`: an empty pattern matches an empty text.

## Zero-or-more, not one-or-more

`"a*"` matches `""`. Requiring at least one is the single most common misreading, and the
`table[i][j-2]` term is exactly what encodes it.

## Whole match, not a prefix

The answer is the corner cell `table[len(text)][len(pattern)]`. Returning `True` as soon as
some prefix matches accepts `"aa"` against `"a"`.

## Greedy fails

`".*"` could absorb everything, but if the pattern continues — `".*b"` — it must give some back.
`"aab"` against `".*b"` needs `.*` to take only `"aa"`. A greedy longest-first scan gets this
wrong; the table considers both lengths at once.

## Pitfalls

**Skipping the `table[0][j]` initialisation.** Breaks every leading zero-width group.

**Treating `*` as one-or-more.** Wrong for `"a*"` against `""`.

**Reading `pattern[j-2]` without a bounds check.** The problem statement rules out a leading
`*`, but the guard is cheap.

**Checking `text[i-1]` in the empty-text row.** Index error; that row is handled separately.

## Cost

O(len(text) * len(pattern)) time and space.
