# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def overlap_area(a, b):
    ax1, ay1, ax2, ay2 = a
    bx1, by1, bx2, by2 = b

    width = min(ax2, bx2) - max(ax1, bx1)
    height = min(ay2, by2) - max(ay1, by1)

    # A non-positive extent on either axis means no overlap at all. Multiplying
    # two negatives would otherwise produce a positive area from two rectangles
    # that miss each other diagonally.
    if width <= 0 or height <= 0:
        return 0
    return width * height
