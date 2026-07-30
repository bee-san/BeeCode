# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def find_words(board, words):
    if not board or not board[0] or not words:
        return []

    root = {}
    for word in words:
        node = root
        for character in word:
            node = node.setdefault(character, {})
        node["$"] = word

    rows = len(board)
    columns = len(board[0])
    found = set()

    def explore(row, column, node):
        character = board[row][column]
        if character not in node:
            return
        following = node[character]
        if "$" in following:
            found.add(following["$"])
        board[row][column] = None
        for next_row, next_column in (
            (row - 1, column),
            (row + 1, column),
            (row, column - 1),
            (row, column + 1),
        ):
            if 0 <= next_row < rows and 0 <= next_column < columns:
                if board[next_row][next_column] is not None:
                    explore(next_row, next_column, following)
        board[row][column] = character

    for row in range(rows):
        for column in range(columns):
            explore(row, column, root)
    return sorted(found)
