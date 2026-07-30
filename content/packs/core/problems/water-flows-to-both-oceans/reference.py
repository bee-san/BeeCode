# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def both_oceans(heights):
    if not heights or not heights[0]:
        return []

    rows = len(heights)
    columns = len(heights[0])

    def climb(sources):
        reached = set()
        pending = list(sources)
        for cell in sources:
            reached.add(cell)
        while pending:
            row, column = pending.pop()
            for next_row, next_column in (
                (row - 1, column),
                (row + 1, column),
                (row, column - 1),
                (row, column + 1),
            ):
                if not (0 <= next_row < rows and 0 <= next_column < columns):
                    continue
                if (next_row, next_column) in reached:
                    continue
                if heights[next_row][next_column] < heights[row][column]:
                    continue
                reached.add((next_row, next_column))
                pending.append((next_row, next_column))
        return reached

    first = []
    second = []
    for column in range(columns):
        first.append((0, column))
        second.append((rows - 1, column))
    for row in range(rows):
        first.append((row, 0))
        second.append((row, columns - 1))

    shared = climb(first) & climb(second)
    answer = []
    for row, column in sorted(shared):
        answer.append([row, column])
    return answer
