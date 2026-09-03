# Test fixtures

`phonepe_sample.pdf` is a synthetic PhonePe-shaped statement. It exists so the parser can be
tested through the *same* path the app uses — a real PDF file, read by the real
`PdfBoxStatementTextExtractor` — rather than by handing a text string straight to the parser.

That distinction is the whole point. Text fixtures encode one person's guess about how PdfBox
lays out a page, and a guess written by whoever also wrote the parser agrees with the parser by
construction. A real June 2026 statement lost 5 of its 142 transactions while every text-fixture
test passed. Verifying against a different PDF reader is no better: a check run with the
`pdftotext` CLI reported an edge case that does not exist under the library the app actually
ships.

Regenerate with:

    python3 tools/make_phonepe_fixture.py core/pdf/src/androidTest/assets/phonepe_sample.pdf

The generator is committed alongside the PDF so the fixture can be extended — a new row shape
belongs in the script, not hand-patched into the binary.

No real statement data is committed here. Every payee, phone number, reference and UTR is
invented; only the *layout* is copied from real statements, including the awkward parts: a payee
containing the word CREDIT, opening phrases the parser has no name for, a payee that wraps onto
the next line, and rows separated by a page break with repeated column headers.
