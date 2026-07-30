# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def cheapest_path(grid):
    columns = len(grid[0])
    best = [0] * columns

    for row_index, row in enumerate(grid):
        for column in range(columns):
            if row_index == 0 and column == 0:
                best[column] = row[column]
            elif row_index == 0:
                best[column] = best[column - 1] + row[column]
            elif column == 0:
                best[column] = best[column] + row[column]
            else:
                if best[column - 1] < best[column]:
                    best[column] = best[column - 1] + row[column]
                else:
                    best[column] = best[column] + row[column]
    return best[columns - 1]
