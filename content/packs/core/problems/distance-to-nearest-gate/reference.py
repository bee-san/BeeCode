# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def fill_distances(rooms):
    if not rooms or not rooms[0]:
        return rooms

    empty = 2147483647
    rows = len(rooms)
    columns = len(rooms[0])

    frontier = []
    for row in range(rows):
        for column in range(columns):
            if rooms[row][column] == 0:
                frontier.append((row, column))

    distance = 0
    while frontier:
        distance += 1
        following = []
        for row, column in frontier:
            for next_row, next_column in (
                (row - 1, column),
                (row + 1, column),
                (row, column - 1),
                (row, column + 1),
            ):
                if not (0 <= next_row < rows and 0 <= next_column < columns):
                    continue
                if rooms[next_row][next_column] != empty:
                    continue
                rooms[next_row][next_column] = distance
                following.append((next_row, next_column))
        frontier = following
    return rooms
