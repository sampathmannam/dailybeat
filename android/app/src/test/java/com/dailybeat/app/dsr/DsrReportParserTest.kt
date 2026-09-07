package com.dailybeat.app.dsr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DsrReportParserTest {
    private val parser = DsrReportParser()

    // Every fixture below is deliberately fictional and uses future years and
    // synthetic identifiers. No operational PDF content belongs in source control.

    @Test
    fun parsesDailySummaryCasesAndForecastWithoutPersonalNarratives() {
        val text = """
            DAILY DSR 01.01.2099
            Police Station Advance Forecast 02.01.99
            Rasipuram At 11.00 AM a synthetic protest drill will be held. Crowd: About 123 persons.
            Vennandur Nil

            Station Progress
            Rasipuram 3 2 - 4 3 2 1 5 6 1 - -
            Vennandur 3 1 - 5 4 3 2 7 8 1 - -
            Total 6 3 - 9 7 5 3 12 14 2 - -

            I-Reported Cases
            S.No Head Station Cr No Sec of Law
            1. 194 BNSS Rasipuram 9001/99 194 BNSS
            2. Cotpa Rasipuram 9002/99 24(1) COTPA Act
            3. Prohibition Vennandur 9003/99 4(A) TNP Act
            4. Namagiripettai 9004/99 281,125(a) BNS
               Non Fatal
            5. Ayilpatty 9005/99
               Others 291 BNS
            6. Non Fatal Mangalapuram 9006/99 281,125(a) BNS
            I-Charged Cases 2.O

            e-Summon Statistical report
            Total 40 - 40 5 4 9 0 31
            e-Sakhya Progress
            Total 2 3 20 18 7 9
            FRS Query Count Progress
            NBW Pending 01.01.99
            SDO Total 11 - 11 - - 11
            C.No.999-9/TEST/2099 e-filed rectification
            SDO Total 20 1 7 1 7 13
            Station CCTV Camera Particulars
        """.trimIndent()

        val parsed = parser.parse("01.01.99 SYNTHETIC DSR.pdf", listOf(text))

        assertEquals(OperationalReportType.DSR, parsed.reportType)
        assertEquals("2099-01-01", parsed.reportDate)
        assertEquals(6, parsed.cases.size)
        assertEquals("NAMAGIRIPET", parsed.cases.first { it.crimeNumber == "9004" }.stationCode)
        assertEquals(DsrPriority.HIGH, parsed.forecasts.single().priority)
        assertEquals(123, parsed.forecasts.single().expectedCrowd)
        assertEquals(31, metricValue(parsed, "esummon.pending"))
        assertEquals(7, metricValue(parsed, "esakshya.pending"))
        assertEquals(11, metricValue(parsed, "nbw.pending"))
        assertEquals(13, metricValue(parsed, "efile_rectification.pending"))
        assertTrue(parsed.issues.none { it.code == "REPORTED_CASE_COUNT_MISMATCH" })
    }

    @Test
    fun preservesConflictingRowsAndFlagsCrimeNumberCollision() {
        val text = """
            DAILY DSR 02.01.2099
            Station Progress
            Total 2 - - - - - - - - - - -
            I-Reported Cases
            1. Missing Namagiripet 9999/99 Man Missing
            2. Prohibition Namagiripet 9999/99 4(A) TNP Act
            I-Charged Cases 2.O
        """.trimIndent()

        val parsed = parser.parse("02.01.99 SYNTHETIC DSR.pdf", listOf(text))

        assertEquals(2, parsed.cases.size)
        assertTrue(parsed.cases.all { it.needsReview })
        assertTrue(parsed.issues.any { it.code == "CRIME_NUMBER_COLLISION" })
    }

    @Test
    fun flagsRepeatedCasePagesAndLegalSectionMismatch() {
        val summary = """
            DAILY DSR 03.01.2099
            Station Progress
            Total 1 - - - - - - - - - - -
            I-Reported Cases
            1. Theft Ayilpatty 8888/99 303(3) BNS
            I-Charged Cases 2.O
        """.trimIndent()
        val firstDetail = """
            Ayilpatty Police Station
            Cr. No. 8888/2099
            Section 303(2) BNS - property detail redacted
        """.trimIndent()
        val duplicatedDetail = firstDetail.replace("property detail", "duplicate detail")

        val parsed = parser.parse("03.01.99 SYNTHETIC DSR.pdf", listOf(summary, firstDetail, duplicatedDetail))

        assertTrue(parsed.issues.any { it.code == "CASE_REPEATED_ACROSS_PAGES" })
        assertTrue(parsed.issues.any { it.code == "CASE_SECTION_MISMATCH" })
    }

    @Test
    fun detectsTasmacDetailAndSummaryMismatch() {
        val text = """
            RASIPURAM SUB-DIVISION - TASMAC DETAILS - 04.01.2099
            SDO Total 2 6 6 1 1
            1. Rasipuram 99001 Synthetic address 3 3 7 days Contact redacted Yes
            2. Rasipuram 99002 Synthetic address 3 3 7 days Contact redacted Yes
        """.trimIndent()

        val parsed = parser.parse("SYNTHETIC TASMAC DETAILS -04.01.99.pdf", listOf(text))

        assertEquals(OperationalReportType.TASMAC, parsed.reportType)
        assertEquals(2, metricValue(parsed, "tasmac.shops"))
        assertTrue(parsed.issues.any { it.code == "TASMAC_DETAIL_SUMMARY_MISMATCH" })
    }

    @Test
    fun detectsFatalAccidentTitlePeriodMismatch() {
        val text = """
            ROAD SAFETY MEETING ANALYSIS OF FATAL ACCIDENTS OCCURRED IN JANUARY 2099
            1 Example Station Alpha 7001/99 18.02.2099 20.30 TEST-1 Bus Pedestrian
            2 Example Station Beta 7002/99 20.02.2099 16.40 TEST-2 Two Wheeler Pedestrian
            NH - Accident Death SOC
            Total No. of Accident: 01
            SH - Accident Death SOC
            Total No. of Accident: 02
            MDR & ODR - Accident Death SOC
            Total No. of Accident: 01
        """.trimIndent()

        val parsed = parser.parse("SYNTHETIC Fatal Accident Report.pdf", listOf(text))

        assertEquals(OperationalReportType.FATAL_ACCIDENT, parsed.reportType)
        assertEquals(4, metricValue(parsed, "traffic.fatal_accident_cases"))
        assertEquals("2099-02-01", parsed.reportDate)
        assertTrue(parsed.issues.any { it.code == "ACCIDENT_PERIOD_MISMATCH" })
    }

    @Test
    fun parsesAllCrimeAggregatePageAndFlagsBadPrintedPercentage() {
        val summaryPage = """
            RASIPURAM SUB-DIVISION
            1) ALL CRIME CASES REPORTED- STATION WISE - (01.01.2098 TO 31.12.2098)
            Station Reported Detected % Property Lost Recovery %
            Rasipuram 4 3 50% 40000 30000 75%
            Vennandur 3 2 67% 30000 21000 70%
            Namagiripet 2 2 100% 20000 20000 100%
            Belukurichi 1 0 0% 10000 0 0%
            Ayilpatty 2 1 50% 20000 10000 50%
            Mangalapuram 1 1 100% 10000 10000 100%
            Total 13 9 69% 130000 91000 70%

            SUB DIVISION ALL CRIME AS ON : 31.12.2098
            Head Rep Det Con Acq PT UI OD
            Murder for gain - - - - - - -
            Dacoity 1 1 - - 1 - -
            Robbery 1 1 - - 1 - -
            Snatching 1 1 - - 0 1 -
            H.B. Day 2 1 - - 1 1 -
            H. B Night 2 2 - - 2 0 -
            Theft - A 5 3 - - 2 3 -
            Theft - B 0 0 - - 0 0 -
            Theft - C 1 0 - - 0 0 1
            Total 13 9 - - 7 5 1
            % of Detection 69%
            Property Lost Rs. 130000/-
            Property recovered Rs. 91000/-
            % of recovery 70%
        """.trimIndent()
        val privateDetailPage = "A later case-detail page that is intentionally outside the aggregate parser."

        val parsed = parser.parse(
            "SYNTHETIC ALL CRIME AS ON-31.12.2098.pdf",
            listOf(summaryPage, privateDetailPage),
        )

        assertEquals(OperationalReportType.ALL_CRIME, parsed.reportType)
        assertEquals("2098-12-31", parsed.reportDate)
        assertEquals(13, metricValue(parsed, "allcrime.reported"))
        assertEquals(9, metricValue(parsed, "allcrime.detected"))
        assertEquals(4, metricValue(parsed, "allcrime.station.RASIPURAM.reported"))
        assertEquals(5, metricValue(parsed, "allcrime.head.THEFT_A.reported"))
        assertEquals(5, metricValue(parsed, "allcrime.head.TOTAL.under_investigation"))
        assertEquals(12, metricValue(parsed, "allcrime.period_months"))
        assertTrue(parsed.issues.any { it.code == "ALL_CRIME_DETECTION_PERCENT_MISMATCH" })
        assertTrue(parsed.issues.none { it.code == "ALL_CRIME_STATION_SUM_MISMATCH" })
    }

    private fun metricValue(report: ParsedOperationalReport, code: String): Int {
        val metric = report.metrics.firstOrNull { it.metricCode == code }
        assertNotNull("Missing metric $code", metric)
        return requireNotNull(metric).value
    }
}
