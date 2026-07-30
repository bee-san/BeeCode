# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def cheapest_descent(rows):
    best = list(rows[len(rows) - 1])
    for index in range(len(rows) - 2, -1, -1):
        row = rows[index]
        for position in range(len(row)):
            if best[position] < best[position + 1]:
                best[position] = row[position] + best[position]
            else:
                best[position] = row[position] + best[position + 1]
    return best[0]
