"""Build the indexed 16x16 HUD icons without replacing the custom armour art."""
import struct


def build_hud_icons(read_cel, armor):
    if len(armor) != 16 * 16:
        raise ValueError("HUD armour icon must contain 256 palette indices")

    def crop(name, cx, cy, cw, ch):
        data = read_cel(name)
        _, width, height = struct.unpack("<3H", data[:6])
        if cx < 0 or cy < 0 or cx + cw > width or cy + ch > height or len(data) < 800 + width * height:
            raise ValueError("invalid HUD source crop: " + name)
        pixels = data[800:800 + width * height]
        return bytes(pixels[(cy + y * ch // 16) * width + cx + x * cw // 16]
                     for y in range(16) for x in range(16))

    icons = (crop("NSTAT.CEL", 5, 16, 24, 24), armor,
             crop("WICONS.CEL", 3, 3, 20, 20))
    return b"".join(struct.pack(">HH", 16, 16) + icon for icon in icons)
