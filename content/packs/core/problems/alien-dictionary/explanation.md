## The insight

Two steps, and the first is where the thinking is.

**Extract the facts.** For each adjacent pair of words, find the first position where they
differ. That single comparison is the only information the pair carries:

```python
for position in range(min(len(first), len(second))):
    if first[position] != second[position]:
        add_edge(first[position], second[position])
        break                            # everything after this tells you nothing
```

The `break` is essential. `"wrt"` before `"wrf"` says `t` comes before `f` and says nothing
about `w` or `r` — they are equal, not ordered — and nothing about any later position,
because the words are already separated.

**Topologically sort.** Kahn's algorithm over the letters, exactly as in
[An Order That Satisfies Every Prerequisite](course-order). A cycle means no ordering
exists.

## Two ways to be inconsistent

**A cycle.** `z < x` and `x < z`. Caught by the usual count: if the sort emits fewer
letters than exist, something was stuck in a cycle.

**A prefix that comes second.** `["abc", "ab"]` is impossible — a prefix must sort first —
and there is no cycle to find, because the pair yields no differing position at all. This
needs its own explicit check, and it is the case most solutions miss:

```python
if not differed and len(first) > len(second):
    return ""
```

## Only the letters present

The alphabet is the letters that actually appear, not all 26. Seeding all of them adds
letters to the output that the input never mentioned.

## The tie-break

Using a heap for the ready set instead of a queue emits the smallest available letter each
time, which yields the ordinary-dictionary-smallest of the valid orderings. That is not
part of the classic problem — it is here so the expected values are unambiguous — but a
heap in place of a queue is a genuinely useful trick for making any topological sort
deterministic.

## Pitfalls

**Not breaking after the first difference.** Invents ordering facts, usually producing
`""` on valid input.

**Skipping the prefix check.** `["abc", "ab"]` returns `"abc"` instead of `""`.

**Counting duplicate edges.** The same pair of letters can be implied twice; incrementing
the count twice means it never reaches zero. Guard with a set, as above.

**Comparing only the first characters.** Words sharing a prefix carry their information
further in.

## Cost

O(total input length + letters^2) time in the worst case, O(letters^2) space for the edges.
