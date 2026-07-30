# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def overlaps(first, second):
    left = 0
    right = 0
    found = []
    while left < len(first) and right < len(second):
        begin = first[left][0]
        if second[right][0] > begin:
            begin = second[right][0]
        finish = first[left][1]
        if second[right][1] < finish:
            finish = second[right][1]
        if begin <= finish:
            found.append([begin, finish])
        if first[left][1] < second[right][1]:
            left += 1
        else:
            right += 1
    return found
