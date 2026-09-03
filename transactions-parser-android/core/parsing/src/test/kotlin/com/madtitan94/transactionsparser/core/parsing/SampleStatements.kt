package com.madtitan94.transactionsparser.core.parsing

/**
 * Text fixtures in the shapes PdfBox produces for the real sample statements.
 *
 * Payees, phone numbers, references and UTRs are synthetic — this repo is public and real
 * statements are not committed — but the *row shapes* are copied from actual extraction output,
 * including the ones that used to be dropped. The earlier version of this file was typed by hand
 * from the parser's own assumptions and contained only "Paid to" rows, so every test passed while
 * a real statement quietly lost 5 of its 142 transactions.
 */
object SampleStatements {

    /**
     * Columns on the row's own line, as PdfBox emits when it keeps the table together.
     *
     * Six rows, deliberately awkward: a plain debit, a payee containing the word CREDIT on a debit
     * row, a credit, two phrases the parser has no name for ("Payment to", "Mobile recharged"), and
     * a "Paid to" whose payee wrapped onto the following line.
     */
    val PHONEPE = """
        Transaction Statement for 9000000000
        01 Jul, 2026 - 31 Jul, 2026
        Date            Transaction Details                          Type      Amount
        Jul 03, 2026 Paid to SRI DATTA SUPER SHOPPE                  DEBIT     ₹10
        01:46 PM     Transaction ID T2607031346510066848829
                     UTR No. 930722951778
                     Paid by 5969XXXXXXX0143
        Jul 03, 2026 Paid to SHRIRAM CREDIT CO-OP SOCIETY            DEBIT     ₹55
        01:31 PM     Transaction ID T2607031331208307813752
                     UTR No. 830278002610
                     Paid by 5969XXXXXXX0143
        Jul 02, 2026 Received from PARVATI MAHENDRA DAS              CREDIT    ₹30
        06:22 PM     Transaction ID T2607021822344280465189
                     UTR No. 098591186952
                     Paid by 5969XXXXXXX0143
        Jul 02, 2026 Payment to Acme Insurance Brokers               DEBIT     ₹1,287.89
        02:41 PM     Transaction ID OLEX2607021441483611905461
                     UTR No. 767591658726
                     Paid by 5969XXXXXXX0143
        Jul 01, 2026 Mobile recharged 9000000001                     DEBIT     ₹904
        11:00 AM     Transaction ID NX2607011100374975945309
                     UTR No. 511850842067
                     Jio Prepaid Reference ID 25989739401
                     Paid by 5969XXXXXXX0143
        Jul 01, 2026 Paid to                                         DEBIT     ₹65
        07:57 AM     TOPIC PRODUCTION PRIVATE LIMITED
                     Transaction ID T2607010757085644872096
                     UTR No. 888965926776
                     Paid by 5969XXXXXXX0143
    """.trimIndent()

    /**
     * The same six transactions as [PHONEPE], as PdfBox emits when it reads the page in column
     * order instead: the Type and Amount land *after* the row's reference lines. Both shapes come
     * out of the same library on the same file depending on how the page is laid out, so both have
     * to parse to identical transactions.
     */
    val PHONEPE_COLUMNS_LAST = """
        Transaction Statement for 9000000000
        01 Jul, 2026 - 31 Jul, 2026
        Date

        Transaction Details

        Type

        Amount

        Jul 03, 2026 Paid to SRI DATTA SUPER SHOPPE
        01:46 PM
        Transaction ID T2607031346510066848829
        UTR No. 930722951778
        Paid by 5969XXXXXXX0143

        DEBIT

        ₹10

        Jul 03, 2026 Paid to SHRIRAM CREDIT CO-OP SOCIETY
        01:31 PM
        Transaction ID T2607031331208307813752
        UTR No. 830278002610
        Paid by 5969XXXXXXX0143

        DEBIT

        ₹55

        Jul 02, 2026 Received from PARVATI MAHENDRA DAS
        06:22 PM
        Transaction ID T2607021822344280465189
        UTR No. 098591186952
        Paid by 5969XXXXXXX0143

        CREDIT

        ₹30

        Jul 02, 2026
        02:41 PM

        Payment to Acme Insurance Brokers
        Transaction ID OLEX2607021441483611905461
        UTR No. 767591658726
        Paid by 5969XXXXXXX0143

        DEBIT

        ₹1,287.89

        Jul 01, 2026 Mobile recharged 9000000001
        11:00 AM
        Transaction ID NX2607011100374975945309
        UTR No. 511850842067
        Jio Prepaid Reference ID 25989739401
        Paid by 5969XXXXXXX0143

        DEBIT

        ₹904

        Jul 01, 2026 Paid to
        07:57 AM
        TOPIC PRODUCTION PRIVATE LIMITED
        Transaction ID T2607010757085644872096
        UTR No. 888965926776
        Paid by 5969XXXXXXX0143

        DEBIT

        ₹65
    """.trimIndent()

    val GOOGLE_PAY = """
        Google Pay Transaction statement
        9000000000,
        account.holder@example.com
        Transaction statement period Sent Received
        01 June 2026 - 30 June 2026 ₹2,785 ₹0
        Date & time Transaction details Amount
        09 Jun, 2026 Paid to Mahavitaran - Maharashtra Electricity (MSEDCL) ₹2,580
        10:59 AM UPI Transaction ID: 652624029988
        Paid by Union Bank of India 0143
        23 Jun, 2026 Paid to Blinkit ₹205
        09:03 PM UPI Transaction ID: 617478704091
        Paid by Union Bank of India 0143
    """.trimIndent()

    val NOT_A_STATEMENT = """
        Annual Report 2026
        Company financials and shareholder information.
        Revenue grew 12% year over year.
    """.trimIndent()
}
