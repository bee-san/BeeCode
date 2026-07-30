# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def multiply(first, second):
    if first == "0" or second == "0":
        return "0"

    columns = [0] * (len(first) + len(second))
    for left in range(len(first) - 1, -1, -1):
        for right in range(len(second) - 1, -1, -1):
            product = int(first[left]) * int(second[right])
            position = left + right + 1
            columns[position] += product

    carry = 0
    for position in range(len(columns) - 1, -1, -1):
        total = columns[position] + carry
        columns[position] = total % 10
        carry = total // 10

    digits = []
    started = False
    for value in columns:
        if value != 0:
            started = True
        if started:
            digits.append(str(value))
    if not digits:
        return "0"
    return "".join(digits)
