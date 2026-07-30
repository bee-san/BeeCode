# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def matches_abbreviation(word, abbreviation):
    word_at = 0
    abbreviation_at = 0
    while abbreviation_at < len(abbreviation):
        character = abbreviation[abbreviation_at]
        if character.isdigit():
            if character == "0":
                return False
            length = 0
            while abbreviation_at < len(abbreviation) and abbreviation[abbreviation_at].isdigit():
                length = length * 10 + int(abbreviation[abbreviation_at])
                abbreviation_at += 1
            word_at += length
        else:
            if word_at >= len(word) or word[word_at] != character:
                return False
            word_at += 1
            abbreviation_at += 1
    return word_at == len(word)
