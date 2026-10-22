"""Original geometric illustrations for engineering smoke tests, NOT photo benchmarks."""
from pathlib import Path
from PIL import Image, ImageDraw

DEST = Path(__file__).resolve().parents[1] / "data/examples"


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    image = Image.new("RGB", (960, 720), "#b6d8ec")
    d = ImageDraw.Draw(image)
    d.rectangle((0, 310, 960, 720), fill="#8aa879")
    d.polygon([(440, 310), (510, 310), (840, 720), (140, 720)], fill="#b9b4a7")
    d.line([(465, 330), (430, 720)], fill="#ece8df", width=5)
    for x, y, size in [(110, 230, 85), (800, 235, 90), (900, 275, 65)]:
        d.rectangle((x-10, y, x+10, y+180), fill="#776148")
        d.ellipse((x-size, y-size, x+size, y+size), fill="#527354")
    # One full-body person, deliberately off-centre.
    d.ellipse((536, 240, 598, 310), fill="#e1af88", outline="#6b4f3d", width=3)
    d.polygon([(540, 305), (593, 305), (618, 438), (522, 438)], fill="#d66f56")
    d.line([(541, 326), (512, 407), (518, 450)], fill="#e1af88", width=19)
    d.line([(591, 326), (627, 402), (616, 447)], fill="#e1af88", width=19)
    d.polygon([(531, 434), (566, 434), (557, 572), (529, 572)], fill="#303b4d")
    d.polygon([(567, 434), (603, 434), (618, 572), (588, 572)], fill="#303b4d")
    d.ellipse((514, 561, 558, 583), fill="#333438")
    d.ellipse((586, 561, 634, 583), fill="#333438")
    image.save(DEST / "person_road.png")

    image = Image.new("RGB", (960, 720), "#b9dced")
    d = ImageDraw.Draw(image)
    d.ellipse((740, 70, 820, 150), fill="#f4df9b")
    d.polygon([(0, 385), (210, 145), (430, 385)], fill="#6b8595")
    d.polygon([(290, 385), (550, 100), (850, 385)], fill="#516b7c")
    d.polygon([(476, 184), (550, 100), (641, 187), (578, 173), (542, 191), (520, 172)], fill="#eeede7")
    d.polygon([(670, 385), (840, 210), (960, 340), (960, 385)], fill="#78919c")
    d.rectangle((0, 385, 960, 720), fill="#75afbd")
    for y, x in [(420, 270), (465, 460), (530, 140), (610, 560), (665, 300)]:
        d.line((x, y, x+190, y), fill="#b5d9df", width=4)
    d.polygon([(0, 580), (170, 610), (300, 720), (0, 720)], fill="#5a7959")
    image.save(DEST / "mountain_lake.png")
    Image.new("RGB", (960, 720), "#d4d2cb").save(DEST / "blank.png")
    print("Created 3 original synthetic engineering fixtures (no real photos).")


if __name__ == "__main__":
    main()
