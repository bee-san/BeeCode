# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def partition(text):
    found = []
    pieces = []

    def is_palindrome(low, high):
        while low < high:
            if text[low] != text[high]:
                return False
            low += 1
            high -= 1
        return True

    def build(start):
        if start == len(text):
            found.append(list(pieces))
            return
        for end in range(start, len(text)):
            if is_palindrome(start, end):
                pieces.append(text[start:end + 1])
                build(end + 1)
                pieces.pop()

    build(0)
    return found
