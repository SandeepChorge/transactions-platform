package com.madtitan94.transactionsparser.core.parsing

/** Text fixtures approximating PdfBox extraction of the real sample statements. */
object SampleStatements {

    val PHONEPE = """
        Transaction Statement for 9766789876
        01 Jul, 2026 - 31 Jul, 2026
        Date Transaction Details Type Amount
        Jul 03, 2026 Paid to SRI DATTA SUPER SHOPPE DEBIT ₹10
        01:46 PM Transaction ID T2607031346510066848829
        UTR No. 930722951778
        Paid by 5969XXXXXXX0143
        Jul 03, 2026 Paid to AWDHOOT SNACKS CENTRE DEBIT ₹55
        01:31 PM Transaction ID T2607031331208307813752
        UTR No. 830278002610
        Paid by 5969XXXXXXX0143
        Jul 03, 2026 Paid to AWDHOOT SNACKS CENTRE DEBIT ₹35
        11:00 AM Transaction ID T2607031100374975945309
        UTR No. 511850842067
        Paid by 5969XXXXXXX0143
        Jul 02, 2026 Paid to PARVATI MAHENDRA DAS DEBIT ₹30
        06:22 PM Transaction ID T2607021822344280465189
        UTR No. 098591186952
        Paid by 5969XXXXXXX0143
        Jul 02, 2026 Paid to FULABHAI JETABHAI VERANA DEBIT ₹24
        02:41 PM Transaction ID T2607021441483611905461
        UTR No. 767591658726
        Paid by 5969XXXXXXX0143
    """.trimIndent()

    val GOOGLE_PAY = """
        Google Pay Transaction statement
        9766789876,
        sandeep.chorge94@gmail.com
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
