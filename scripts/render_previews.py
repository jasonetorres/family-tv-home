#!/usr/bin/env python3
"""Preview screenshots with the example household, not a real one."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "screenshots"
WALL = ROOT / "launcher/res/drawable-nodpi/wpa_golden.jpg"
W, H = 1920, 1080


def font(size, bold=False):
    candidates = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def glass(base, box, fill=(255, 255, 255, 70), radius=28):
    overlay = Image.new("RGBA", base.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=(255, 255, 255, 90), width=2)
    blurred = base.filter(ImageFilter.GaussianBlur(8))
    crop = blurred.crop(box)
    tint = Image.new("RGBA", crop.size, fill)
    crop = Image.alpha_composite(crop.convert("RGBA"), tint)
    base.paste(crop, (box[0], box[1]))
    ImageDraw.Draw(base).rounded_rectangle(box, radius=radius, outline=(255, 214, 120, 180), width=2)


def home():
    img = Image.open(WALL).convert("RGBA").resize((W, H))
    d = ImageDraw.Draw(img)
    d.text((72, 48), "7:42 PM", font=font(42, True), fill=(255, 255, 255, 230))
    d.text((72, 150), "Hello, Example family", font=font(64, True), fill=(255, 255, 255, 245))
    d.text((74, 230), "Monday, November 2", font=font(28), fill=(255, 255, 255, 210))
    # countdown
    glass(img, (1320, 48, 1848, 128), (20, 40, 30, 90), 24)
    ImageDraw.Draw(img).text((1350, 72), "54 days until Christmas", font=font(26, True), fill=(255, 255, 255, 240))
    # wide continue watching
    glass(img, (72, 320, 1180, 640), (12, 18, 32, 80))
    ImageDraw.Draw(img).text((100, 348), "Continue watching", font=font(28, True), fill=(255, 255, 255, 230))
    ImageDraw.Draw(img).text((100, 420), "The Example Show", font=font(40, True), fill=(255, 255, 255, 245))
    ImageDraw.Draw(img).text((100, 490), "S2  E4   ·   22 min left", font=font(26), fill=(255, 255, 255, 210))
    # weather card
    glass(img, (1210, 320, 1848, 640), (255, 255, 255, 50))
    draw = ImageDraw.Draw(img)
    draw.text((1240, 348), "Example City", font=font(26, True), fill=(255, 255, 255, 230))
    draw.text((1240, 400), "54°", font=font(72, True), fill=(255, 255, 255, 250))
    draw.text((1480, 450), "Partly cloudy", font=font(26), fill=(255, 255, 255, 220))
    draw.text((1240, 540), "Now   4 PM   5 PM   6 PM   7 PM", font=font(22), fill=(255, 255, 255, 210))
    draw.text((1240, 578), "54°    53°    51°    49°    48°", font=font(22), fill=(255, 255, 255, 230))
    # note + fact
    glass(img, (72, 670, 960, 860), (255, 255, 255, 46))
    ImageDraw.Draw(img).text((100, 694), "Note from Alex", font=font(26, True), fill=(255, 255, 255, 235))
    ImageDraw.Draw(img).text((100, 748), "Movie night is Friday. You pick the film.", font=font(28), fill=(255, 255, 255, 240))
    glass(img, (990, 670, 1848, 860), (255, 255, 255, 46))
    ImageDraw.Draw(img).text((1018, 694), "Fact of the day", font=font(26, True), fill=(255, 255, 255, 235))
    ImageDraw.Draw(img).text((1018, 748), "Honey does not spoil when it is sealed and kept dry.", font=font(26), fill=(255, 255, 255, 240))
    # app row
    apps = [("YouTube", (220, 40, 40)), ("YouTube TV", (180, 30, 30)), ("Netflix", (20, 20, 20)), ("Disney+", (20, 40, 120)), ("Plex", (30, 30, 30))]
    x = 72
    for name, color in apps:
        glass(img, (x, 900, x + 280, 1020), color + (160,), 36)
        ImageDraw.Draw(img).text((x + 24, 938), name, font=font(28, True), fill=(255, 255, 255, 245))
        x += 300
    img.convert("RGB").save(OUT / "home.png", quality=90)


def notes():
    img = Image.new("RGB", (W, H), (92, 58, 36))
    d = ImageDraw.Draw(img)
    for i in range(0, W, 28):
        d.line((i, 0, i, H), fill=(110, 72, 46))
    for i in range(0, H, 28):
        d.line((0, i, W, i), fill=(110, 72, 46))
    d.rounded_rectangle((70, 40, 560, 140), 12, fill=(245, 240, 230))
    d.text((96, 62), "Notes from Alex", font=font(40, True), fill=(40, 24, 16))
    cards = [
        (180, 200, (255, 243, 163), "Movie night", "Friday. You pick the film."),
        (720, 240, (255, 209, 220), "Homework first", "Then the park, if it's still light."),
        (180, 560, (205, 231, 255), "Proud of you", "Be kind to each other today."),
        (760, 600, (212, 245, 201), "Water", "Drink some, then go play."),
    ]
    for x, y, color, title, body in cards:
        d.rounded_rectangle((x, y, x + 460, y + 240), 8, fill=color)
        d.rectangle((x + 180, y - 16, x + 280, y + 18), fill=(230, 80, 70))
        d.text((x + 28, y + 48), title, font=font(36, True), fill=(40, 28, 18))
        d.text((x + 28, y + 120), body, font=font(26), fill=(50, 36, 24))
    d.rounded_rectangle((1320, 48, 1840, 120), 28, fill=(185, 215, 247))
    d.text((1360, 68), "Home in 12 days", font=font(28, True), fill=(20, 40, 70))
    img.save(OUT / "notes.png", quality=90)


def travel():
    img = Image.new("RGB", (W, H), (14, 36, 64))
    d = ImageDraw.Draw(img)
    d.ellipse((180, 120, 1500, 980), outline=(70, 140, 180), width=3)
    d.polygon([(420, 360), (560, 300), (700, 380), (640, 520), (480, 500)], fill=(34, 90, 70))
    d.polygon([(860, 280), (1100, 260), (1180, 420), (980, 480)], fill=(34, 90, 70))
    d.line((640, 430, 1000, 360), fill=(255, 214, 120), width=6)
    d.ellipse((620, 410, 660, 450), fill=(255, 255, 255))
    d.ellipse((980, 340, 1024, 384), fill=(255, 120, 90))
    d.text((72, 40), "Where's Alex", font=font(48, True), fill=(255, 255, 255))
    d.text((72, 110), "Alex lands in 2h 10m", font=font(32), fill=(255, 230, 180))
    d.rounded_rectangle((1360, 200, 1840, 860), 24, fill=(255, 248, 231))
    d.text((1400, 240), "Upcoming", font=font(28, True), fill=(180, 50, 40))
    lines = ["Mon Jun 1, 9:00 AM", "UA100  PHL → ORD", "Stay in Chicago", "Thu Jun 4  UA200 home"]
    y = 320
    for line in lines:
        d.text((1400, y), line, font=font(24), fill=(20, 32, 60))
        y += 70
    img.save(OUT / "travel.png", quality=90)


def ticker():
    img = Image.new("RGB", (W, H), (8, 10, 16))
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, W, H), fill=(18, 22, 32))
    d.text((80, 360), "Some other app is full screen", font=font(42), fill=(180, 186, 198))
    d.rounded_rectangle((40, 920, 1880, 1040), 40, fill=(12, 14, 20))
    d.rounded_rectangle((70, 948, 170, 1010), 16, fill=(200, 32, 40))
    d.text((92, 962), "LIVE", font=font(26, True), fill=(255, 255, 255))
    d.text((200, 960), "★  GB  24    CHI  17   ·  Q4  2:11", font=font(32, True), fill=(255, 255, 255))
    d.text((980, 960), "BOS  88    NY  91   ·  Q3  6:40", font=font(32), fill=(220, 224, 230))
    img.save(OUT / "ticker.png", quality=90)


def weather():
    img = Image.open(ROOT / "launcher/res/drawable-nodpi/wpa_clear_day.jpg").convert("RGB").resize((W, H))
    d = ImageDraw.Draw(img)
    d.text((80, 70), "Example City", font=font(42, True), fill=(255, 255, 255))
    d.text((80, 130), "Monday, November 2", font=font(28), fill=(255, 255, 255))
    d.text((1500, 70), "7:42 PM", font=font(42), fill=(255, 255, 255))
    d.text((80, 280), "54°", font=font(140, True), fill=(255, 255, 255))
    d.text((420, 360), "Partly cloudy", font=font(40), fill=(255, 255, 255))
    d.text((80, 500), "Feels like 51°    Wind 8 mph    Humidity 46%", font=font(28), fill=(255, 255, 255))
    d.rounded_rectangle((1500, 150, 1840, 220), 24, fill=(255, 255, 255))
    d.text((1540, 168), "Now", font=font(28, True), fill=(20, 32, 60))
    for i, (label, temp) in enumerate([("4 PM", "53°"), ("5 PM", "51°"), ("6 PM", "49°"), ("7 PM", "48°")]):
        x = 80 + i * 280
        d.rounded_rectangle((x, 640, x + 250, 860), 28, outline=(255, 255, 255), width=3)
        d.text((x + 24, 680), label, font=font(28, True), fill=(255, 255, 255))
        d.text((x + 24, 750), temp, font=font(40, True), fill=(255, 255, 255))
    img.save(OUT / "weather.png", quality=90)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    home()
    notes()
    travel()
    ticker()
    weather()
    print("wrote", OUT)


if __name__ == "__main__":
    main()
