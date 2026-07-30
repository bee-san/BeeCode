## The insight

XOR sets a bit exactly when the two inputs *differ* there. And because a higher bit
outweighs every lower bit combined, the largest XOR is found greedily from the top:
if any pair can differ at the current bit, take it — no arrangement of lower bits
could ever compensate for giving it up.

So the real question is: *given the bits I have already committed to, does a pair
exist that also differs here?* That is a prefix query, and a prefix query wants a
trie.

## A trie of bits

Store every number as a root-to-leaf path, one bit per level, most significant
first. Then for each `value`, walk down looking for its **opposite** bit at every
step. Each time the opposite branch exists you gain that bit; when it does not, you
are forced to follow the matching branch and gain nothing there.

```python
def maximum_xor(nums):
    if len(nums) < 2:
        return 0

    width = max(max(nums).bit_length(), 1)

    root = {}
    for value in nums:
        node = root
        for bit_index in range(width - 1, -1, -1):
            bit = (value >> bit_index) & 1
            node = node.setdefault(bit, {})

    best = 0
    for value in nums:
        node = root
        current = 0
        for bit_index in range(width - 1, -1, -1):
            bit = (value >> bit_index) & 1
            wanted = 1 - bit
            if wanted in node:
                current |= 1 << bit_index
                node = node[wanted]
            else:
                node = node[bit]
        if current > best:
            best = current
    return best
```

Points that matter:

**Every number goes in the trie, including the one being queried.** That seems to
allow pairing a value with itself, giving `0` — which is harmless, because `0` can
never beat a genuine pair, and the problem's answer is `0` anyway when no better
pair exists. Excluding it would need deletion or counts for no benefit.

**Fixed width, most significant bit first.** Walking low-to-high abandons the
greedy argument entirely: you would trade a bit worth 1 against one worth 64.

**`max(..., 1)` for the width.** An all-zeroes input has `bit_length() == 0`, and a
zero-level trie has no path to walk at all.

**Greedy is provably optimal here** — unusually for a greedy method. Bit `b`
contributes `2^b`, and all lower bits together contribute at most `2^b - 1`, so
securing a higher bit is never the wrong trade.

## Cost

O(n · w) time and O(n · w) space, where `w` is the bit width — 31 here, so
effectively linear.

The brute force is O(n²): at 20,000 elements that is 200 million pairs, against
about 600,000 bit steps for this.
