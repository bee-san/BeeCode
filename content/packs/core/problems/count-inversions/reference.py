# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def count_inversions(nums):
    def sort_and_count(values):
        if len(values) <= 1:
            return values, 0

        middle = len(values) // 2
        left, left_count = sort_and_count(values[:middle])
        right, right_count = sort_and_count(values[middle:])

        merged = []
        total = left_count + right_count
        i = j = 0
        while i < len(left) and j < len(right):
            if left[i] <= right[j]:
                merged.append(left[i])
                i += 1
            else:
                # left[i] and everything after it in `left` are greater than
                # right[j], and all of them sit at earlier positions.
                merged.append(right[j])
                j += 1
                total += len(left) - i
        merged.extend(left[i:])
        merged.extend(right[j:])
        return merged, total

    return sort_and_count(list(nums))[1]
