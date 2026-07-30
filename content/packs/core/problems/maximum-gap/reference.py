# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def maximum_gap(nums):
    count = len(nums)
    if count < 2:
        return 0

    lowest = min(nums)
    highest = max(nums)
    if lowest == highest:
        return 0

    # Buckets narrower than the average gap. The maximum gap is at least the
    # average, so its two ends cannot share a bucket -- which means only each
    # bucket's smallest and largest value can ever matter.
    span = max(1, (highest - lowest) // (count - 1))
    bucket_count = (highest - lowest) // span + 1
    smallest = [None] * bucket_count
    largest = [None] * bucket_count

    for value in nums:
        index = (value - lowest) // span
        if smallest[index] is None or value < smallest[index]:
            smallest[index] = value
        if largest[index] is None or value > largest[index]:
            largest[index] = value

    best = 0
    previous = None
    for index in range(bucket_count):
        if smallest[index] is None:
            continue
        if previous is not None and smallest[index] - previous > best:
            best = smallest[index] - previous
        previous = largest[index]
    return best
