# The trusted reference solution.
#
# This file is used at build time to prove every declared test actually passes,
# and is then EXCLUDED from the shipped pack. A learner never receives it. The
# validator asserts its absence, because shipping it would hand over the answer.

def collide(asteroids):
    survivors = []
    for asteroid in asteroids:
        alive = True
        while alive and asteroid < 0 and survivors and survivors[-1] > 0:
            if survivors[-1] < -asteroid:
                survivors.pop()
            elif survivors[-1] == -asteroid:
                survivors.pop()
                alive = False
            else:
                alive = False
        if alive:
            survivors.append(asteroid)
    return survivors
