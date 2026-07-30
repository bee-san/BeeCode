# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def is_valid_sudoku(grid):
    rows = [set() for _ in range(9)]
    columns = [set() for _ in range(9)]
    boxes = [set() for _ in range(9)]
    for row in range(9):
        for column in range(9):
            digit = grid[row][column]
            if digit == ".":
                continue
            box = (row // 3) * 3 + column // 3
            if digit in rows[row] or digit in columns[column] or digit in boxes[box]:
                return False
            rows[row].add(digit)
            columns[column].add(digit)
            boxes[box].add(digit)
    return True
