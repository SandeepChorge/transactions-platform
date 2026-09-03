#!/usr/bin/env python3
"""Generates the synthetic PhonePe statement PDF used by core:pdf's instrumented tests.

Writes a PDF by hand rather than pulling in a reporting library, because what matters here is
control over *geometry*. The parser's behaviour depends entirely on the order PdfBox emits text
in, and that order comes from the x/y position of each string — so the fixture has to place the
Date, Transaction Details, Type and Amount columns at real coordinates, span two pages, and
repeat the column headers after the page break, exactly as a statement does.

The rupee sign is the one awkward part. Helvetica has no such glyph, so the font declares an
encoding difference mapping byte 128 to the glyph name `uni20B9`; PdfBox resolves that name back
to U+20B9 when extracting. The page would not *render* a rupee sign, which does not matter — this
file is only ever read as text.

Usage:
    python3 tools/make_phonepe_fixture.py <output.pdf>
"""

import sys

RUPEE = "\200"  # byte 128, mapped to uni20B9 by the font's /Differences array

PAGE_W, PAGE_H = 612, 792
COL_DATE, COL_DETAILS, COL_TYPE, COL_AMOUNT = 60, 150, 400, 480

# Six transactions totalling 2,351.89 — deliberately awkward, and matching the text fixtures in
# core:parsing so the two suites describe the same statement.
ROWS = [
    dict(date="Jul 03, 2026", time="01:46 PM", details=["Paid to SRI DATTA SUPER SHOPPE"],
         type="DEBIT", amount="10", ref="T2607031346510066848829", utr="930722951778", extra=None),
    # The payee contains the word CREDIT on a row that is a DEBIT.
    dict(date="Jul 03, 2026", time="01:31 PM", details=["Paid to SHRIRAM CREDIT CO-OP SOCIETY"],
         type="DEBIT", amount="55", ref="T2607031331208307813752", utr="830278002610", extra=None),
    dict(date="Jul 02, 2026", time="06:22 PM", details=["Received from PARVATI MAHENDRA DAS"],
         type="CREDIT", amount="30", ref="T2607021822344280465189", utr="098591186952", extra=None),
    # An opening phrase the parser has no name for, and a reference that is not a T id.
    dict(date="Jul 02, 2026", time="02:41 PM", details=["Payment to Acme Insurance Brokers"],
         type="DEBIT", amount="1,287.89", ref="OLEX2607021441483611905461", utr="767591658726",
         extra=None),
    dict(date="Jul 01, 2026", time="11:00 AM", details=["Mobile recharged 9000000001"],
         type="DEBIT", amount="904", ref="NX2607011100374975945309", utr="511850842067",
         extra="Jio Prepaid Reference ID 25989739401"),
    # The payee wraps onto the line below, leaving "Paid to" alone on the row's own line.
    dict(date="Jul 01, 2026", time="07:57 AM",
         details=["Paid to", "TOPIC PRODUCTION PRIVATE LIMITED"],
         type="DEBIT", amount="65", ref="T2607010757085644872096", utr="888965926776", extra=None),
]

# Split across a page boundary so the fixture also covers a footer and a repeated header
# appearing between two transactions.
ROWS_PER_PAGE = 4


def escape(text):
    return text.replace("\\", r"\\").replace("(", r"\(").replace(")", r"\)")


def show(x, y, text, size=9):
    return f"BT /F1 {size} Tf 1 0 0 1 {x} {y} Tm ({escape(text)}) Tj ET\n"


def column_headers(y):
    return (
        show(COL_DATE, y, "Date", 10)
        + show(COL_DETAILS, y, "Transaction Details", 10)
        + show(COL_TYPE, y, "Type", 10)
        + show(COL_AMOUNT, y, "Amount", 10)
    )


def render_row(row, y):
    """One transaction block. Returns (content, height consumed)."""
    content = show(COL_DATE, y, row["date"])
    content += show(COL_DETAILS, y, row["details"][0])
    content += show(COL_TYPE, y, row["type"])
    content += show(COL_AMOUNT, y, RUPEE + row["amount"])

    line = y - 13
    content += show(COL_DATE, line, row["time"])
    for extra_detail in row["details"][1:]:
        content += show(COL_DETAILS, line, extra_detail)
        line -= 13
    content += show(COL_DETAILS, line, f"Transaction ID {row['ref']}")
    line -= 13
    content += show(COL_DETAILS, line, f"UTR No. {row['utr']}")
    if row["extra"]:
        line -= 13
        content += show(COL_DETAILS, line, row["extra"])
    line -= 13
    content += show(COL_DETAILS, line, "Paid by 5969XXXXXXX0143")

    return content, y - (line - 12)


def build_pages():
    pages = []
    chunks = [ROWS[i:i + ROWS_PER_PAGE] for i in range(0, len(ROWS), ROWS_PER_PAGE)]

    for index, chunk in enumerate(chunks):
        content = ""
        y = PAGE_H - 60

        if index == 0:
            content += show(COL_DATE, y, "Transaction Statement for 9000000000", 13)
            y -= 18
            content += show(COL_DATE, y, "01 Jul, 2026 - 31 Jul, 2026", 10)
            y -= 30

        content += column_headers(y)
        y -= 24

        for row in chunk:
            block, height = render_row(row, y)
            content += block
            y -= height + 12

        content += show(COL_DATE, 50, f"Page {index + 1} of {len(chunks)}", 8)
        content += show(
            COL_DETAILS, 50, "This is a system generated statement.", 8
        )
        pages.append(content)

    return pages


def build_pdf(pages):
    """Assembles the objects into a PDF with a correct xref table."""
    page_count = len(pages)
    # 1 catalog, 2 pages tree, 3 font, 4 encoding, then a page + contents object per page.
    first_page_obj = 5
    page_ids = [first_page_obj + i * 2 for i in range(page_count)]

    objects = {
        1: "<< /Type /Catalog /Pages 2 0 R >>",
        2: "<< /Type /Pages /Kids [%s] /Count %d >>"
           % (" ".join(f"{pid} 0 R" for pid in page_ids), page_count),
        3: "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding 4 0 R >>",
        4: "<< /Type /Encoding /BaseEncoding /WinAnsiEncoding /Differences [128 /uni20B9] >>",
    }

    for i, content in enumerate(pages):
        page_id = page_ids[i]
        objects[page_id] = (
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 %d %d] "
            "/Resources << /Font << /F1 3 0 R >> >> /Contents %d 0 R >>"
            % (PAGE_W, PAGE_H, page_id + 1)
        )
        objects[page_id + 1] = (
            "<< /Length %d >>\nstream\n%s\nendstream" % (len(content.encode("latin-1")), content)
        )

    out = bytearray(b"%PDF-1.4\n")
    offsets = {}
    for number in sorted(objects):
        offsets[number] = len(out)
        out += f"{number} 0 obj\n{objects[number]}\nendobj\n".encode("latin-1")

    xref_at = len(out)
    highest = max(objects)
    out += f"xref\n0 {highest + 1}\n".encode("latin-1")
    out += b"0000000000 65535 f \n"
    for number in range(1, highest + 1):
        out += f"{offsets[number]:010d} 00000 n \n".encode("latin-1")
    out += (
        f"trailer\n<< /Size {highest + 1} /Root 1 0 R >>\nstartxref\n{xref_at}\n%%EOF\n"
    ).encode("latin-1")

    return bytes(out)


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    with open(sys.argv[1], "wb") as handle:
        handle.write(build_pdf(build_pages()))
    print(f"wrote {sys.argv[1]}")


if __name__ == "__main__":
    main()
