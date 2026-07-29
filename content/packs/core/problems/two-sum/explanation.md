## The insight

Checking every pair works, but asks the wrong question. Instead of "do these two
numbers add to the target?", ask **"have I already seen the number this one needs?"**

For each value, its partner is exactly `target - value`. If you remember every
value you have already passed, and where you saw it, then answering that question
is one lookup.

## One pass

```python
def two_sum(nums, target):
    seen = {}                          # value -> index
    for index, value in enumerate(nums):
        complement = target - value
        if complement in seen:
            return [seen[complement], index]
        seen[value] = index
```

Two details are easy to get wrong:

**Check before you insert.** If you store `value` first and then look for its
complement, an element whose value is exactly half the target will match itself
and return the same index twice. Checking first makes this impossible rather than
guarded against.

**Store the index, not just membership.** A set tells you the partner exists; a
dict tells you *where*, which is what the answer needs.

## Cost

O(n) time and O(n) space — one pass, and at most `n` entries remembered.

The nested-loop version is O(n²). At 20,000 elements that is around 200 million
comparisons, which is why this Problem's larger test cases have a time limit: the
insight is the point, so it is enforced rather than merely recommended.
