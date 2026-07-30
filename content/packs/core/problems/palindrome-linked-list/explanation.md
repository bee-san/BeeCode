## The insight

With the values in a list, two indices closing in from both ends settle it in one pass and O(1)
space:

```python
low, high = 0, len(values) - 1
while low < high:
    if values[low] != values[high]:
        return False
    low, high = low + 1, high - 1
return True
```

The loop stops when the indices meet or cross, so the middle element of an odd-length sequence is
never compared against itself — which is correct, because it always matches.

## The real linked-list version

A chain cannot be walked backwards, so the O(1)-space method is:

1. **Find the middle** with a slow pointer moving one node and a fast pointer moving two. When
   `fast` reaches the end, `slow` is at the middle.
2. **Reverse the second half** in place, from `slow` onward.
3. **Compare** the first half against the reversed second half, walking both forwards.
4. **Reverse the second half back**, restoring the caller's structure.

Step 4 is the one that gets skipped. It makes no difference to the returned answer, which is why it
survives most test suites — and it leaves the caller's list silently mangled. A function that
answers a question should not rearrange its input; if it must, it should put it back.

## Why the middle need not be exact

For odd lengths the middle node belongs to neither half and can be compared against itself
harmlessly, so the two-pointer walk needs no length parity check. That is the same reason the index
loop above uses `low < high` rather than `low <= high`.

## Why the copy is fine

`values == values[::-1]` is one line, O(n) space, and perfectly correct. The exercise is the O(1)
in-place version, and it is worth being honest that the JSON list representation hands you the easy
route — which is why the follow-up names the harder one explicitly.

## Pitfalls

**Not restoring the reversed half.** Correct answer, damaged input.

**Comparing halves without accounting for an odd length.** The middle node is in neither half.

**An empty chain or a single node.** Both are palindromes.

**Using `low <= high`.** Compares the middle against itself. Harmless, and it says you have not
thought about where the loop ends.

## Cost

O(n) time, O(1) space.
