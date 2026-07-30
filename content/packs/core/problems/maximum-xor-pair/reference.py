# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def maximum_xor(nums):
    if len(nums) < 2:
        return 0

    width = max(max(nums).bit_length(), 1)

    # A binary trie: each level is one bit, most significant first.
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
