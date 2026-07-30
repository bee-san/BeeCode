# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def word_exists(board, word):
    if not word:
        return True
    if not board or not board[0]:
        return False

    rows = len(board)
    columns = len(board[0])

    def explore(row, column, position):
        if board[row][column] != word[position]:
            return False
        if position == len(word) - 1:
            return True
        held = board[row][column]
        board[row][column] = None
        for next_row, next_column in (
            (row - 1, column),
            (row + 1, column),
            (row, column - 1),
            (row, column + 1),
        ):
            if 0 <= next_row < rows and 0 <= next_column < columns:
                if explore(next_row, next_column, position + 1):
                    board[row][column] = held
                    return True
        board[row][column] = held
        return False

    for row in range(rows):
        for column in range(columns):
            if explore(row, column, 0):
                return True
    return False
