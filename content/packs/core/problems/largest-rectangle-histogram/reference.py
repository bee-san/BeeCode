# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def largest_rectangle(heights):
    stack = []
    best = 0
    for index in range(len(heights) + 1):
        if index == len(heights):
            height = 0
        else:
            height = heights[index]
        while stack and heights[stack[-1]] >= height:
            top = stack.pop()
            if stack:
                left = stack[-1] + 1
            else:
                left = 0
            area = heights[top] * (index - left)
            if area > best:
                best = area
        stack.append(index)
    return best
