# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def matches(text, pattern):
    rows = len(text) + 1
    columns = len(pattern) + 1
    table = [[False] * columns for _ in range(rows)]
    table[0][0] = True

    for column in range(1, columns):
        if pattern[column - 1] == "*" and column >= 2:
            table[0][column] = table[0][column - 2]

    for row in range(1, rows):
        for column in range(1, columns):
            symbol = pattern[column - 1]
            if symbol == "*":
                if column < 2:
                    continue
                previous = pattern[column - 2]
                if table[row][column - 2]:
                    table[row][column] = True
                elif previous == "." or previous == text[row - 1]:
                    table[row][column] = table[row - 1][column]
            elif symbol == "." or symbol == text[row - 1]:
                table[row][column] = table[row - 1][column - 1]

    return table[len(text)][len(pattern)]
