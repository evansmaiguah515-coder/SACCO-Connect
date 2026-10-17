#!/usr/bin/env python3
"""Turns real emulator screenshots into the 'full-page screenshots' PDF required by the brief.

1. Run the app in the emulator or on a phone and capture each screen:
     adb exec-out screencap -p > screenshots/01_splash.png
   (or use the camera icon in the Android Studio emulator toolbar)
2. Name files with a number prefix so they sort in order, e.g. 01_splash.png, 02_register.png ...
3. pip install pillow reportlab
4. python tools/screenshots_to_pdf.py screenshots SACCO_Screenshots.pdf
"""
import sys, os
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader

REQUIRED = ["Splash screen", "Registration screen", "Login screen", "Member dashboard", "Member profile", "Savings screen",
            "Savings transaction history", "Loan application", "Loan status", "Loan repayment", "Transaction history",
            "Shares/contributions", "Notifications", "Administrator login", "Administrator dashboard", "Member management",
            "Loan management", "Reports/analytics", "Logout"]

folder, out = sys.argv[1], sys.argv[2]
files = sorted(f for f in os.listdir(folder) if f.lower().endswith((".png", ".jpg", ".jpeg")))
if len(files) < len(REQUIRED):
    print(f"Warning: {len(files)} images found, the brief lists {len(REQUIRED)} required screens.")
W, H = A4
c = canvas.Canvas(out, pagesize=A4)
for i, f in enumerate(files):
    label = REQUIRED[i] if i < len(REQUIRED) else os.path.splitext(f)[0]
    img = ImageReader(os.path.join(folder, f)); iw, ih = img.getSize()
    maxw, maxh = W - 60, H - 120
    s = min(maxw / iw, maxh / ih)
    c.setFont("Helvetica-Bold", 14); c.drawCentredString(W / 2, H - 40, f"Figure {i+1}: {label}")
    c.drawImage(img, (W - iw * s) / 2, 50, iw * s, ih * s)
    c.setFont("Helvetica", 8); c.drawCentredString(W / 2, 25, "SACCO Connect - STU 3102")
    c.showPage()
c.save(); print("Wrote", out, "with", len(files), "pages")
