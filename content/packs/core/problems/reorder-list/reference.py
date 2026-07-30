# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def reorder(values):
    if len(values) <= 2:
        return list(values)

    middle = (len(values) + 1) // 2
    front = values[:middle]
    back = values[middle:]
    back.reverse()

    woven = []
    for index in range(len(front)):
        woven.append(front[index])
        if index < len(back):
            woven.append(back[index])
    return woven
