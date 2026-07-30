# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def capture(board):
    if not board or not board[0]:
        return board

    rows = len(board)
    columns = len(board[0])

    def keep(start_row, start_column):
        if board[start_row][start_column] != "O":
            return
        pending = [(start_row, start_column)]
        board[start_row][start_column] = "S"
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
                if board[next_row][next_column] != "O":
                    continue
                board[next_row][next_column] = "S"
                pending.append((next_row, next_column))

    for column in range(columns):
        keep(0, column)
        keep(rows - 1, column)
    for row in range(rows):
        keep(row, 0)
        keep(row, columns - 1)

    for row in range(rows):
        for column in range(columns):
            if board[row][column] == "O":
                board[row][column] = "X"
            elif board[row][column] == "S":
                board[row][column] = "O"
    return board
