# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def rectangle_sums(matrix, queries):
    if not matrix or not matrix[0]:
        return [0] * len(queries)

    rows = len(matrix)
    cols = len(matrix[0])

    # One row and column of zeroes on the top and left, so a query touching an
    # edge needs no special case.
    totals = [[0] * (cols + 1) for _ in range(rows + 1)]
    for r in range(rows):
        for c in range(cols):
            totals[r + 1][c + 1] = (
                matrix[r][c] + totals[r][c + 1] + totals[r + 1][c] - totals[r][c]
            )

    answers = []
    for row1, col1, row2, col2 in queries:
        answers.append(
            totals[row2 + 1][col2 + 1]
            - totals[row1][col2 + 1]
            - totals[row2 + 1][col1]
            + totals[row1][col1]
        )
    return answers
