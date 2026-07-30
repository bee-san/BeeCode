# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def triangle(rows):
    result = []
    for index in range(rows):
        row = [1] * (index + 1)
        for position in range(1, index):
            row[position] = result[index - 1][position - 1] + result[index - 1][position]
        result.append(row)
    return result
