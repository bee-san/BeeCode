# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def add_digit_lists(left, right):
    digits = []
    carry = 0
    index = 0
    while index < len(left) or index < len(right) or carry:
        total = carry
        if index < len(left):
            total += left[index]
        if index < len(right):
            total += right[index]
        digits.append(total % 10)
        carry = total // 10
        index += 1
    if not digits:
        digits.append(0)
    return digits
