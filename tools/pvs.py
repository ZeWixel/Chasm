"""Conservative PVS for the rectangular Chasm-to-C3B map compiler.

Reject a pair only when one opaque, axis-aligned boundary segment separates
both complete sector bounds and covers every possible crossing of a ray joining
them. A straight ray between the boxes must then cross that wall. Bounds are
expanded by two cells to protect models extending past their owning sector. This
is intentionally much less aggressive than portal-beam visibility.
"""


def build_pvs(rects, solid_segments, margin=2):
    bounds = [(x0-margin, y0-margin, x1+margin, y1+margin)
              for x0, y0, x1, y1, *_ in rects]
    vertical, horizontal = [], []
    for x0, y0, x1, y1 in solid_segments:
        if x0 == x1 and y0 != y1:
            vertical.append((x0, min(y0, y1), max(y0, y1)))
        elif y0 == y1 and x0 != x1:
            horizontal.append((y0, min(x0, x1), max(x0, x1)))
    def merge(segments):
        # Contiguous solid edges form a longer opaque segment. Never bridge gaps.
        result = []
        for coord, lo, hi in sorted(segments):
            if result and result[-1][0] == coord and lo <= result[-1][2]:
                prev = result[-1]
                result[-1] = (coord, prev[1], max(prev[2], hi))
            else:
                result.append((coord, lo, hi))
        return result
    vertical, horizontal = merge(vertical), merge(horizontal)
    n = len(bounds)
    hidden = [bytearray(n) for _ in range(n)]
    for i in range(n):
        a = bounds[i]
        for j in range(i):
            b = bounds[j]
            # A boundary strictly between the boxes is an occluder only if it
            # covers the entire range of crossing points. For fixed endpoints'
            # X coordinates, the extreme crossings use the low/low and high/high
            # Y corners. The resulting linear-fractional function is monotone in
            # each X endpoint, so checking all endpoint X corners is conservative.
            def covers(a0, a1, al, ah, b0, b1, bl, bh, walls):
                if b1 < a0:
                    a0, a1, al, ah, b0, b1, bl, bh = b0, b1, bl, bh, a0, a1, al, ah
                if a1 >= b0:
                    return False
                for x, lo, hi in walls:
                    if not a1 < x < b0:
                        continue
                    enclosed = True
                    for ax in (a0, a1):
                        for bx in (b0, b1):
                            den = bx - ax
                            low = al * (bx - x) + bl * (x - ax)
                            high = ah * (bx - x) + bh * (x - ax)
                            if low <= lo * den or high >= hi * den:
                                enclosed = False
                                break
                        if not enclosed:
                            break
                    if enclosed:
                        return True
                return False
            blocked = covers(a[0], a[2], a[1], a[3], b[0], b[2], b[1], b[3], vertical)
            if not blocked:
                blocked = covers(a[1], a[3], a[0], a[2], b[1], b[3], b[0], b[2], horizontal)
            if blocked:
                hidden[i][j] = hidden[j][i] = 1
    return hidden
