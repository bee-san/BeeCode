# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.


def median_of_sorted(a, b):
    # Binary search over the shorter list, so the bound is log(min(n, m)) and the
    # cut in `b` is always within range.
    if len(a) > len(b):
        a, b = b, a

    n, m = len(a), len(b)
    total = n + m
    half = (total + 1) // 2

    low, high = 0, n
    while low <= high:
        take_a = (low + high) // 2
        take_b = half - take_a

        # Values just outside a cut are infinities, which removes every boundary
        # special case: an empty side can never be the binding constraint.
        left_a = a[take_a - 1] if take_a > 0 else float("-inf")
        right_a = a[take_a] if take_a < n else float("inf")
        left_b = b[take_b - 1] if take_b > 0 else float("-inf")
        right_b = b[take_b] if take_b < m else float("inf")

        if left_a <= right_b and left_b <= right_a:
            # Correct split: everything left of both cuts is <= everything right.
            if total % 2 == 1:
                return float(max(left_a, left_b))
            return (max(left_a, left_b) + min(right_a, right_b)) / 2.0

        if left_a > right_b:
            # Took too many from `a`.
            high = take_a - 1
        else:
            low = take_a + 1

    # Unreachable for sorted inputs; a guard rather than a silent wrong answer.
    raise ValueError("inputs were not sorted ascending")
