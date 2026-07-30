# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def days_until_warmer(temperatures):
    waiting = []
    answer = [0] * len(temperatures)
    for day, temperature in enumerate(temperatures):
        while waiting and temperatures[waiting[-1]] < temperature:
            earlier = waiting.pop()
            answer[earlier] = day - earlier
        waiting.append(day)
    return answer
