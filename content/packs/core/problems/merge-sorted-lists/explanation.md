## The insight

Ask what the *next* element of the answer is, not what the whole answer is.

The output is built left to right, smallest first. At any moment, some prefix of `a`
and some prefix of `b` have already been emitted. The smallest element still
unemitted must be at the front of one of the two remaining suffixes — because each list
is sorted, so nothing deeper in a list can be smaller than that list's own front.

So you only ever compare two candidates. One pointer per list, take the smaller,
advance that pointer. This is the merge step of merge sort, and it is worth learning as
a shape rather than as a trick: whenever you have several sorted sequences and want them
combined, you compare only their heads.

## Two pointers

```python
def merge_sorted(a, b):
    merged = []
    i = j = 0
    while i < len(a) and j < len(b):
        if a[i] <= b[j]:
            merged.append(a[i])
            i += 1
        else:
            merged.append(b[j])
            j += 1
    merged.extend(a[i:])
    merged.extend(b[j:])
    return merged
```

The loop stops as soon as *either* list is exhausted, which leaves a tail behind. The
two `extend` calls drain it. Only one of them can be non-empty, and appending the
remainder wholesale is correct precisely because whatever is left is already sorted and
is entirely ≥ everything emitted so far.

Three things to get right:

**Do not forget the drain.** This is the bug. `[1, 2, 3]` merged with `[7, 8, 9]`
exits the loop after three iterations with `7, 8, 9` still unwritten. A solution that
returns `merged` straight after the loop passes the interleaved example and silently
truncates here — which is why the suite tests both "a entirely first" and "b entirely
first".

**Advance exactly one pointer per iteration.** On a tie it is tempting to emit both
equal values and advance both. That is fine only if you emit both; if you advance both
after emitting one, you drop an element. The `[2, 2, 2]` / `[2, 2]` case is all ties, so
any such bug shows up immediately as a short result. Emitting one element and advancing
one pointer keeps the loop trivially correct.

**Use `<=`, not `<`, and think about why.** With `<` the code is still correct for
integers, since equal values are interchangeable. But taking from `a` on ties makes the
merge *stable* — the relative order of equal elements follows the input order — and
stability is what you need the moment the elements are records sorted by a key rather
than bare numbers. Free property; take it.

## Cost

O(len(a) + len(b)) time: every element is examined once and appended once. O(n) space for
the output, which the problem requires, plus O(1) working memory.

`sorted(a + b)` is O(n log n) and discards the sortedness you were handed. It is the
right answer only when you cannot rely on the inputs being sorted — here you can.
