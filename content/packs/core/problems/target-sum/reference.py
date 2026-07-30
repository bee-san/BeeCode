# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def count_sign_assignments(values, target):
    ways = {0: 1}
    for value in values:
        following = {}
        for total in ways:
            for signed in (total + value, total - value):
                if signed in following:
                    following[signed] += ways[total]
                else:
                    following[signed] = ways[total]
        ways = following
    if target in ways:
        return ways[target]
    return 0
