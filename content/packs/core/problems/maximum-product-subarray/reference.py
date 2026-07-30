# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def max_product(values):
    if not values:
        return 0

    largest = values[0]
    smallest = values[0]
    best = values[0]

    for index in range(1, len(values)):
        value = values[index]
        by_extending_largest = largest * value
        by_extending_smallest = smallest * value

        next_largest = value
        if by_extending_largest > next_largest:
            next_largest = by_extending_largest
        if by_extending_smallest > next_largest:
            next_largest = by_extending_smallest

        next_smallest = value
        if by_extending_largest < next_smallest:
            next_smallest = by_extending_largest
        if by_extending_smallest < next_smallest:
            next_smallest = by_extending_smallest

        largest = next_largest
        smallest = next_smallest
        if largest > best:
            best = largest
    return best
