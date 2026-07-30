# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_decodings(digits):
    if not digits:
        return 0

    two_back = 1
    one_back = 1
    if digits[0] == "0":
        one_back = 0

    for index in range(1, len(digits)):
        ways = 0
        if digits[index] != "0":
            ways += one_back
        pair = int(digits[index - 1:index + 1])
        if 10 <= pair <= 26:
            ways += two_back
        two_back = one_back
        one_back = ways
    return one_back
