# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def solve_queens(n):
    found = []
    columns = set()
    rising = set()
    falling = set()
    placement = []

    def place(row):
        if row == n:
            board = []
            for column in placement:
                board.append("." * column + "Q" + "." * (n - column - 1))
            found.append(board)
            return
        for column in range(n):
            if column in columns:
                continue
            if (row + column) in rising:
                continue
            if (row - column) in falling:
                continue
            columns.add(column)
            rising.add(row + column)
            falling.add(row - column)
            placement.append(column)
            place(row + 1)
            placement.pop()
            falling.discard(row - column)
            rising.discard(row + column)
            columns.discard(column)

    place(0)
    return found
