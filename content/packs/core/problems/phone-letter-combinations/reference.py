# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def letter_combinations(digits):
    groups = {
        "2": "abc",
        "3": "def",
        "4": "ghi",
        "5": "jkl",
        "6": "mno",
        "7": "pqrs",
        "8": "tuv",
        "9": "wxyz",
    }
    if not digits:
        return []

    found = []
    letters = []

    def build(position):
        if position == len(digits):
            found.append("".join(letters))
            return
        for letter in groups[digits[position]]:
            letters.append(letter)
            build(position + 1)
            letters.pop()

    build(0)
    return found
