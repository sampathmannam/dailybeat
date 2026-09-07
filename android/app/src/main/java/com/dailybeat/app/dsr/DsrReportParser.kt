package com.dailybeat.app.dsr

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class DsrReportParser {
    private val dsrDate = Regex("(?i)DAILY\\s+DSR\\s+(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})")
    private val forecastDate = Regex("(?i)Advance\\s+Forecast\\s+(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})")
    private val genericDate = Regex("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})")
    private val allCrimeAsOn = Regex(
        "(?i)SUB\\s+DIVISION\\s+ALL\\s+CRIME\\s+AS\\s+ON\\s*:?\\s*(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})",
    )
    private val allCrimeStationWise = Regex(
        "(?i)ALL\\s+CRIME\\s+CASES\\s+REPORTED\\s*-\\s*STATION\\s+WISE",
    )
    private val allCrimeHeadRow = Regex(
        "^(Murder\\s+for\\s+gain|Dacoity|Robbery|Snatching|H\\.?\\s*B\\.?\\s*Day|" +
            "H\\.?\\s*B\\.?\\s*Night|Theft\\s*-\\s*[ABC]|Total)\\s+(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val crimeRow = Regex(
        "^(?:\\d+[.)]?\\s+)?(.*?)\\s*(${DsrNormalization.stationAlternation})\\s+" +
            "(\\d+)\\s*/\\s*(\\d{2,4})(?:\\s+(.*))?$",
        RegexOption.IGNORE_CASE,
    )

    fun parse(originalFileName: String, pages: List<String>): ParsedOperationalReport {
        require(pages.isNotEmpty()) { "The PDF has no readable pages." }
        val fullText = pages.joinToString("\n\u000C\n")
        val reportType = detectType(fullText)
        return when (reportType) {
            OperationalReportType.DSR -> parseDsr(originalFileName, pages, fullText)
            OperationalReportType.ALL_CRIME -> parseAllCrime(originalFileName, pages)
            OperationalReportType.TASMAC -> parseTasmac(originalFileName, pages, fullText)
            OperationalReportType.FATAL_ACCIDENT -> parseFatalAccidents(originalFileName, pages, fullText)
            OperationalReportType.UNKNOWN -> ParsedOperationalReport(
                reportType = reportType,
                reportDate = dateFromFileName(originalFileName),
                pageCount = pages.size,
                issues = listOf(
                    ParsedDsrIssue(
                        DsrIssueSeverity.ERROR,
                        "UNSUPPORTED_REPORT",
                        "This PDF is not recognised as a Daily DSR, all-crime, TASMAC inventory or fatal-accident report.",
                    ),
                ),
            )
        }
    }

    private fun detectType(text: String): OperationalReportType = when {
        text.contains("DAILY DSR", ignoreCase = true) -> OperationalReportType.DSR
        allCrimeStationWise.containsMatchIn(text) -> OperationalReportType.ALL_CRIME
        text.contains("TASMAC DETAILS", ignoreCase = true) -> OperationalReportType.TASMAC
        text.contains("FATAL ACCIDENT", ignoreCase = true) -> OperationalReportType.FATAL_ACCIDENT
        else -> OperationalReportType.UNKNOWN
    }

    /**
     * All-crime files contain personally identifying case narratives after the
     * summary. Only the first-page aggregate tables are eligible for import.
     */
    private fun parseAllCrime(
        originalFileName: String,
        pages: List<String>,
    ): ParsedOperationalReport {
        val firstPage = pages.first()
        val issues = mutableListOf<ParsedDsrIssue>()
        val reportDate = dateFromMatch(allCrimeAsOn.find(firstPage)) ?: dateFromFileName(originalFileName)
        if (reportDate == null) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "ALL_CRIME_DATE_UNREADABLE",
                "The all-crime period end date could not be read.",
                sourcePage = 1,
            )
        }

        val stationRows = parseAllCrimeStationRows(firstPage)
        val headRows = parseAllCrimeHeadRows(firstPage)
        if (stationRows.none { it.code == "TOTAL" }) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "ALL_CRIME_STATION_TOTAL_UNREADABLE",
                "The all-crime station total row could not be read.",
                sourcePage = 1,
            )
        }
        if (headRows.none { it.code == "TOTAL" }) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "ALL_CRIME_HEAD_TOTAL_UNREADABLE",
                "The all-crime crime-head total row could not be read.",
                sourcePage = 1,
            )
        }

        stationRows.forEach { row ->
            validateAllCrimePercentages(row, issues)
        }
        validateAllCrimeTotals(stationRows, headRows, issues)

        val metrics = mutableListOf<ParsedDsrMetric>()
        fun metric(code: String, value: Int) {
            metrics += ParsedDsrMetric(code, value, DsrMetricSemantics.CUMULATIVE)
        }
        stationRows.forEach { row ->
            val prefix = "allcrime.station.${row.code}"
            metric("$prefix.reported", row.reported)
            metric("$prefix.detected", row.detected)
            metric("$prefix.detection_pct_stated", row.detectionPercent)
            metric("$prefix.property_lost", row.propertyLost)
            metric("$prefix.property_recovered", row.propertyRecovered)
            metric("$prefix.recovery_pct_stated", row.recoveryPercent)
            if (row.code == "TOTAL") {
                metric("allcrime.reported", row.reported)
                metric("allcrime.detected", row.detected)
                metric("allcrime.detection_pct_stated", row.detectionPercent)
                metric("allcrime.property_lost", row.propertyLost)
                metric("allcrime.property_recovered", row.propertyRecovered)
                metric("allcrime.recovery_pct_stated", row.recoveryPercent)
            }
        }
        headRows.forEach { row ->
            val prefix = "allcrime.head.${row.code}"
            metric("$prefix.reported", row.reported)
            metric("$prefix.detected", row.detected)
            metric("$prefix.convicted", row.convicted)
            metric("$prefix.acquitted", row.acquitted)
            metric("$prefix.pending_trial", row.pendingTrial)
            metric("$prefix.under_investigation", row.underInvestigation)
            metric("$prefix.other_disposal", row.otherDisposal)
        }
        reportDate?.let { metric("allcrime.period_months", LocalDate.parse(it).monthValue) }

        if (firstPage.count(Char::isLetterOrDigit) < 200) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "LOW_TEXT_CONTENT",
                "The all-crime summary page has too little readable text for safe extraction.",
                sourcePage = 1,
            )
        }
        return ParsedOperationalReport(
            reportType = OperationalReportType.ALL_CRIME,
            reportDate = reportDate,
            pageCount = pages.size,
            metrics = metrics.distinctBy { it.metricCode },
            issues = issues.distinctBy { listOf(it.code, it.message) },
        )
    }

    private fun parseAllCrimeStationRows(firstPage: String): List<AllCrimeStationRow> {
        val start = allCrimeStationWise.find(firstPage)?.range?.first ?: -1
        val end = allCrimeAsOn.find(firstPage)?.range?.first ?: -1
        if (start < 0 || end <= start) return emptyList()
        return firstPage.substring(start, end).lineSequence().mapNotNull { rawLine ->
            val line = DsrNormalization.whitespace(rawLine)
            val stationAndRemainder = if (line.startsWith("Total ", true)) {
                NormalizedStation("TOTAL", "Subdivision total") to line.substringAfter(' ')
            } else {
                DsrNormalization.stationAtStart(line)
            } ?: return@mapNotNull null
            val values = numericCells(stationAndRemainder.second)
            if (values.size < 6) return@mapNotNull null
            AllCrimeStationRow(
                code = stationAndRemainder.first.code,
                reported = values[0],
                detected = values[1],
                detectionPercent = values[2],
                propertyLost = values[3],
                propertyRecovered = values[4],
                recoveryPercent = values[5],
            )
        }.distinctBy { it.code }.toList()
    }

    private fun parseAllCrimeHeadRows(firstPage: String): List<AllCrimeHeadRow> {
        val start = allCrimeAsOn.find(firstPage)?.range?.first ?: -1
        if (start < 0) return emptyList()
        return firstPage.substring(start).lineSequence().mapNotNull { rawLine ->
            val line = DsrNormalization.whitespace(rawLine)
            val match = allCrimeHeadRow.matchEntire(line) ?: return@mapNotNull null
            val code = allCrimeHeadCode(match.groupValues[1])
            val values = numericCells(match.groupValues[2]).take(7).toMutableList()
            while (values.size < 7) values += 0
            AllCrimeHeadRow(
                code = code,
                reported = values[0],
                detected = values[1],
                convicted = values[2],
                acquitted = values[3],
                pendingTrial = values[4],
                underInvestigation = values[5],
                otherDisposal = values[6],
            )
        }.distinctBy { it.code }.toList()
    }

    private fun numericCells(value: String): List<Int> = Regex("(?:-|\\d+%?)")
        .findAll(value)
        .map { match -> match.value.removeSuffix("%").toIntOrNull() ?: 0 }
        .toList()

    private fun allCrimeHeadCode(value: String): String {
        val compact = DsrNormalization.whitespace(value).uppercase(Locale.ENGLISH)
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
        return when (compact) {
            "H_B_DAY" -> "HB_DAY"
            "H_B_NIGHT" -> "HB_NIGHT"
            else -> compact
        }
    }

    private fun validateAllCrimePercentages(
        row: AllCrimeStationRow,
        issues: MutableList<ParsedDsrIssue>,
    ) {
        val detection = percentage(row.detected, row.reported)
        if (kotlin.math.abs(detection - row.detectionPercent) > 1) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.WARNING,
                "ALL_CRIME_DETECTION_PERCENT_MISMATCH",
                "${allCrimeStationLabel(row.code)} shows ${row.detectionPercent}% detection, but the counts calculate to $detection%.",
                sourcePage = 1,
            )
        }
        val recovery = percentage(row.propertyRecovered, row.propertyLost)
        if (kotlin.math.abs(recovery - row.recoveryPercent) > 1) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.WARNING,
                "ALL_CRIME_RECOVERY_PERCENT_MISMATCH",
                "${allCrimeStationLabel(row.code)} shows ${row.recoveryPercent}% recovery, but the amounts calculate to $recovery%.",
                sourcePage = 1,
            )
        }
    }

    private fun validateAllCrimeTotals(
        stations: List<AllCrimeStationRow>,
        heads: List<AllCrimeHeadRow>,
        issues: MutableList<ParsedDsrIssue>,
    ) {
        val total = stations.firstOrNull { it.code == "TOTAL" } ?: return
        val componentStations = stations.filter { it.code != "TOTAL" }
        val stationSums = listOf(
            componentStations.sumOf { it.reported },
            componentStations.sumOf { it.detected },
            componentStations.sumOf { it.propertyLost },
            componentStations.sumOf { it.propertyRecovered },
        )
        val stated = listOf(total.reported, total.detected, total.propertyLost, total.propertyRecovered)
        if (componentStations.isNotEmpty() && stationSums != stated) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "ALL_CRIME_STATION_SUM_MISMATCH",
                "The station rows do not add up to the all-crime total row.",
                sourcePage = 1,
            )
        }
        heads.firstOrNull { it.code == "TOTAL" }?.let { headTotal ->
            if (headTotal.reported != total.reported || headTotal.detected != total.detected) {
                issues += ParsedDsrIssue(
                    DsrIssueSeverity.ERROR,
                    "ALL_CRIME_HEAD_TOTAL_MISMATCH",
                    "The crime-head and station tables disagree on reported or detected totals.",
                    sourcePage = 1,
                )
            }
        }
    }

    private fun percentage(numerator: Int, denominator: Int): Int =
        if (denominator == 0) 0 else (numerator * 100.0 / denominator).roundToInt()

    private fun allCrimeStationLabel(code: String): String = when (code) {
        "TOTAL" -> "Subdivision total"
        else -> code.lowercase(Locale.ENGLISH).replace('_', ' ').replaceFirstChar(Char::uppercase)
    }

    private data class AllCrimeStationRow(
        val code: String,
        val reported: Int,
        val detected: Int,
        val detectionPercent: Int,
        val propertyLost: Int,
        val propertyRecovered: Int,
        val recoveryPercent: Int,
    )

    private data class AllCrimeHeadRow(
        val code: String,
        val reported: Int,
        val detected: Int,
        val convicted: Int,
        val acquitted: Int,
        val pendingTrial: Int,
        val underInvestigation: Int,
        val otherDisposal: Int,
    )

    private fun parseDsr(
        originalFileName: String,
        pages: List<String>,
        fullText: String,
    ): ParsedOperationalReport {
        val issues = mutableListOf<ParsedDsrIssue>()
        val headerDate = dateFromMatch(dsrDate.find(fullText))
        val fileDate = dateFromFileName(originalFileName)
        val reportDate = headerDate ?: fileDate
        if (reportDate == null) {
            issues += ParsedDsrIssue(DsrIssueSeverity.ERROR, "MISSING_REPORT_DATE", "The DSR date could not be read.")
        }
        if (headerDate != null && fileDate != null && headerDate != fileDate) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "REPORT_DATE_MISMATCH",
                "The filename date and the Daily DSR heading do not match.",
            )
        }

        val rawCases = parseReportedCases(fullText, pages, issues)
        val cases = resolveCaseCollisions(rawCases, issues)
        detectRepeatedCaseReferences(cases, pages, issues)
        detectSectionConflicts(cases, pages, issues)
        val stationSnapshots = parseStationSnapshots(fullText, issues)
        val forecasts = if (reportDate != null) parseForecasts(fullText, reportDate) else emptyList()
        val metrics = parseDsrMetrics(fullText, stationSnapshots)

        val reportedTotal = stationSnapshots.firstOrNull { it.stationCode == "TOTAL" }?.reportedCases
        if (reportedTotal != null && reportedTotal != cases.size) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "REPORTED_CASE_COUNT_MISMATCH",
                "The station-progress total reports $reportedTotal cases, but ${cases.size} distinct table rows were parsed.",
            )
        }
        if (cases.isEmpty()) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.WARNING,
                "NO_REPORTED_CASE_ROWS",
                "No rows were extracted from the reported-cases table; review this import before relying on it.",
            )
        }
        if (pages.sumOf { it.count(Char::isLetterOrDigit) } < 300) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "LOW_TEXT_CONTENT",
                "The document appears image-only or has too little readable text for safe automatic extraction.",
            )
        }

        return ParsedOperationalReport(
            reportType = OperationalReportType.DSR,
            reportDate = reportDate,
            pageCount = pages.size,
            cases = cases,
            stationSnapshots = stationSnapshots,
            forecasts = forecasts,
            metrics = metrics,
            issues = issues.distinctBy { listOf(it.code, it.caseKey, it.message) },
        )
    }

    private fun parseReportedCases(
        fullText: String,
        pages: List<String>,
        issues: MutableList<ParsedDsrIssue>,
    ): List<ParsedDsrCase> {
        val section = section(fullText, "I-Reported Cases", listOf("I-Charged Cases", "I-Charged Cases 2.O"))
            ?: return emptyList()
        val parsed = mutableListOf<ParsedDsrCase>()
        val lines = section.lineSequence().map(DsrNormalization::whitespace).filter { it.isNotBlank() }.toList()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank() || line.contains("Sec of Law", true) || line.contains("S.No", true)) {
                index += 1
                continue
            }
            val match = crimeRow.matchEntire(line)
            if (match == null) {
                index += 1
                continue
            }
            var head = DsrNormalization.whitespace(match.groupValues[1])
            head = when {
                Regex("^\\d+[.)]$").matches(head) -> ""
                Regex("^\\d+[.)]\\s+.+$").matches(head) -> head.replace(Regex("^\\d+[.)]\\s+"), "")
                else -> head
            }
            val initialStation = DsrNormalization.station(match.groupValues[2])
            if (initialStation == null) {
                index += 1
                continue
            }
            var station: NormalizedStation = initialStation
            val crimeNumber = match.groupValues[3]
            val crimeYearRaw = match.groupValues[4].toIntOrNull()
            if (crimeYearRaw == null) {
                index += 1
                continue
            }
            val crimeYear = if (crimeYearRaw < 100) 2000 + crimeYearRaw else crimeYearRaw
            var law = DsrNormalization.whitespace(match.groupValues[5])
            val stationContinuation = lines.getOrNull(index + 1)?.let(DsrNormalization::stationAtStart)
            if (station.code == "RASIPURAM" && stationContinuation?.first?.code == "AWPS_RPM") {
                station = stationContinuation.first
                law = DsrNormalization.whitespace("$law ${stationContinuation.second}")
                index += 1
                issues += ParsedDsrIssue(
                    DsrIssueSeverity.INFO,
                    "WRAPPED_AWPS_STATION",
                    "A wrapped Rasipuram AWPS station row was reconstructed.",
                )
            }
            if (head.isBlank()) {
                val continuation = lines.getOrNull(index + 1).orEmpty()
                if (continuation.isNotBlank() && crimeRow.matchEntire(continuation) == null) {
                    if (law.isNotBlank()) {
                        head = continuation
                    } else {
                        val split = Regex("^(.+?)\\s+(\\d.+)$").matchEntire(continuation)
                        head = split?.groupValues?.get(1) ?: continuation
                        law = split?.groupValues?.get(2).orEmpty()
                    }
                    index += 1
                    issues += ParsedDsrIssue(
                        DsrIssueSeverity.INFO,
                        "WRAPPED_REPORTED_CASE_ROW",
                        "A wrapped reported-case row was reconstructed and should be checked against the source table.",
                    )
                }
            }
            head = normalizeHead(head)
            val baseKey = "${station.code}|$crimeYear|${crimeNumber.trimStart('0').ifEmpty { "0" }}"
            val display = "$crimeNumber/$crimeYear"
            val page = pages.indexOfFirst { pageText ->
                Regex("\\b${Regex.escape(crimeNumber)}\\s*/\\s*(?:${crimeYear % 100}|$crimeYear)\\b")
                    .containsMatchIn(pageText)
            }.takeIf { it >= 0 }?.plus(1)
            parsed += ParsedDsrCase(
                caseKey = baseKey,
                stationCode = station.code,
                stationName = station.displayName,
                crimeNumber = crimeNumber,
                crimeYear = crimeYear,
                displayCrimeNumber = display,
                head = head,
                lawSections = law,
                priority = DsrNormalization.priorityForCase(head, law),
                sourcePage = page,
            )
            index += 1
        }
        if (parsed.any { it.stationCode == "RASIPURAM" && it.head.contains("POCSO", true) }) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.WARNING,
                "POSSIBLE_AWPS_STATION_WRAP",
                "A POCSO row was read as Rasipuram; confirm whether the wrapped station value is Rasipuram AWPS.",
            )
        }
        return parsed
    }

    private fun normalizeHead(value: String): String = when (value.trim().lowercase(Locale.ENGLISH)) {
        "pohibition" -> "Prohibition"
        "n.fatal" -> "Non Fatal"
        else -> value.trim()
    }

    private fun resolveCaseCollisions(
        raw: List<ParsedDsrCase>,
        issues: MutableList<ParsedDsrIssue>,
    ): List<ParsedDsrCase> {
        val result = mutableListOf<ParsedDsrCase>()
        raw.groupBy { it.caseKey }.forEach { (baseKey, rows) ->
            val unique = rows.distinctBy { it.head.uppercase(Locale.ENGLISH) to it.lawSections.uppercase(Locale.ENGLISH) }
            when {
                unique.size == 1 -> {
                    result += unique.first()
                    if (rows.size > 1) {
                        issues += ParsedDsrIssue(
                            DsrIssueSeverity.WARNING,
                            "DUPLICATE_CASE_ROW",
                            "The same station and crime number appears more than once in the reported-cases table.",
                            caseKey = baseKey,
                            sourcePage = unique.first().sourcePage,
                        )
                    }
                }
                else -> {
                    unique.forEachIndexed { index, row ->
                        val key = if (index == 0) baseKey else "$baseKey|CONFLICT-${DsrNormalization.stableId(row.head, row.lawSections)}"
                        result += row.copy(caseKey = key, needsReview = true)
                    }
                    issues += ParsedDsrIssue(
                        DsrIssueSeverity.ERROR,
                        "CRIME_NUMBER_COLLISION",
                        "One station and crime number is assigned to different case heads or sections.",
                        caseKey = baseKey,
                        sourcePage = unique.first().sourcePage,
                    )
                }
            }
        }
        return result
    }

    private fun detectRepeatedCaseReferences(
        cases: List<ParsedDsrCase>,
        pages: List<String>,
        issues: MutableList<ParsedDsrIssue>,
    ) {
        cases.distinctBy { Triple(it.stationCode, it.crimeNumber, it.crimeYear) }.forEach { case ->
            val reference = caseReference(case)
            val matchingPages = pages.mapIndexedNotNull { index, page ->
                (index + 1).takeIf { reference.containsMatchIn(page) }
            }
            if (matchingPages.size > 2) {
                issues += ParsedDsrIssue(
                    DsrIssueSeverity.WARNING,
                    "CASE_REPEATED_ACROSS_PAGES",
                    "The same case reference appears on ${matchingPages.size} pages; check for a duplicate narrative or unintended cross-reference.",
                    caseKey = case.caseKey.substringBefore("|CONFLICT-"),
                    sourcePage = matchingPages[2],
                )
            }
        }
    }

    private fun detectSectionConflicts(
        cases: List<ParsedDsrCase>,
        pages: List<String>,
        issues: MutableList<ParsedDsrIssue>,
    ) {
        cases.distinctBy { Triple(it.stationCode, it.crimeNumber, it.crimeYear) }.forEach { case ->
            val tableSignature = legalSectionSignature(case.lawSections)
            if (tableSignature.isEmpty()) return@forEach
            val reference = caseReference(case)
            val conflictingPage = pages.mapIndexedNotNull { pageIndex, page ->
                if (page.contains("I-Reported Cases", ignoreCase = true) || !reference.containsMatchIn(page)) {
                    return@mapIndexedNotNull null
                }
                val lines = page.lineSequence().map(DsrNormalization::whitespace).toList()
                val referenceLine = lines.indexOfFirst(reference::containsMatchIn)
                if (referenceLine < 0) return@mapIndexedNotNull null
                val sectionText = lines.subList(referenceLine, (referenceLine + 5).coerceAtMost(lines.size))
                    .firstNotNullOfOrNull(::sectionTextFromNarrativeLine)
                    ?: return@mapIndexedNotNull null
                val detailSignature = legalSectionSignature(sectionText)
                (pageIndex + 1).takeIf { detailSignature.isNotEmpty() && detailSignature != tableSignature }
            }.firstOrNull()
            if (conflictingPage != null) {
                issues += ParsedDsrIssue(
                    DsrIssueSeverity.WARNING,
                    "CASE_SECTION_MISMATCH",
                    "The reported-cases table and a case-detail page use different legal-section numbers.",
                    caseKey = case.caseKey.substringBefore("|CONFLICT-"),
                    sourcePage = conflictingPage,
                )
            }
        }
    }

    private fun caseReference(case: ParsedDsrCase): Regex {
        val shortYear = (case.crimeYear % 100).toString().padStart(2, '0')
        return Regex(
            "\\b${Regex.escape(case.crimeNumber)}\\s*/\\s*(?:$shortYear|${case.crimeYear})\\b",
            RegexOption.IGNORE_CASE,
        )
    }

    private fun sectionTextFromNarrativeLine(line: String): String? {
        val normal = DsrNormalization.whitespace(line)
        val underSection = Regex("(?i)\\bU\\s*/\\s*S\\b\\s*:?\\s*").find(normal)
        if (underSection != null) return normal.substring(underSection.range.last + 1)
        val section = Regex("(?i)^SECTION\\s*:?\\s*").find(normal) ?: return null
        return normal.substring(section.range.last + 1)
    }

    private fun legalSectionSignature(value: String): List<String> {
        val clean = DsrNormalization.whitespace(value)
            .uppercase(Locale.ENGLISH)
            .split(Regex("\\s+-\\s+"), limit = 2)
            .first()
            .replace(Regex("(?<=\\d)-(?=[A-Z])"), "")
        return Regex("\\b\\d{1,3}\\s*(?:\\([A-Z0-9]+\\))*[A-Z]?")
            .findAll(clean)
            .map { it.value.replace(Regex("[\\s()]"), "") }
            .toList()
    }

    private fun parseStationSnapshots(
        fullText: String,
        issues: MutableList<ParsedDsrIssue>,
    ): List<ParsedStationSnapshot> {
        val end = fullText.indexOf("I-Reported Cases", ignoreCase = true).takeIf { it >= 0 } ?: return emptyList()
        val start = fullText.lastIndexOf("Station Progress", end, ignoreCase = true).takeIf { it >= 0 } ?: 0
        val block = fullText.substring(start, end)
        val result = mutableListOf<ParsedStationSnapshot>()
        block.lineSequence().forEach { rawLine ->
            val line = DsrNormalization.whitespace(rawLine)
            val stationAndRemainder = if (line.startsWith("Total ", true)) {
                NormalizedStation("TOTAL", "Subdivision total") to line.substringAfter(' ')
            } else {
                DsrNormalization.stationAtStart(line)
            } ?: return@forEach
            val values = Regex("(?<![A-Za-z])(?:-|\\d+)(?![A-Za-z])")
                .findAll(stationAndRemainder.second)
                .map { if (it.value == "-") 0 else it.value.toInt() }
                .toList()
            if (values.size >= 2) {
                val complete = values.size == 12
                result += ParsedStationSnapshot(
                    stationCode = stationAndRemainder.first.code,
                    stationName = stationAndRemainder.first.displayName,
                    reportedCases = values[0],
                    chargedCases = values[1],
                    otherDisposals = values.getOrNull(2).takeIf { complete },
                    eSummonsReceived = values.getOrNull(3).takeIf { complete },
                    eSummonsServed = values.getOrNull(4).takeIf { complete },
                    eSakshyaRecorded = values.getOrNull(5).takeIf { complete },
                    eSakshyaLinked = values.getOrNull(6).takeIf { complete },
                    mvDdCases = values.getOrNull(7).takeIf { complete },
                    mvOtherCases = values.getOrNull(8).takeIf { complete },
                    takenOnFile = values.getOrNull(9).takeIf { complete },
                    convictions = values.getOrNull(10).takeIf { complete },
                    acquittals = values.getOrNull(11).takeIf { complete },
                )
                if (!complete && stationAndRemainder.first.code != "TOTAL" &&
                    issues.none { it.code == "PARTIAL_STATION_METRICS" }
                ) {
                    issues += ParsedDsrIssue(
                        DsrIssueSeverity.INFO,
                        "PARTIAL_STATION_METRICS",
                        "Some blank table cells could not be aligned; reported and charged counts were retained, while uncertain cells were left empty.",
                    )
                }
            } else if (stationAndRemainder.first.code == "TOTAL") {
                issues += ParsedDsrIssue(
                    DsrIssueSeverity.WARNING,
                    "STATION_TOTAL_LAYOUT_UNREADABLE",
                    "The station-progress total row could not be aligned safely and was not imported.",
                )
            }
        }
        return result.distinctBy { it.stationCode }
    }

    private fun parseForecasts(fullText: String, reportDate: String): List<ParsedDsrForecast> {
        val dateMatch = forecastDate.find(fullText)
        val eventDate = dateFromMatch(dateMatch) ?: reportDate
        val start = dateMatch?.range?.last?.plus(1) ?: return emptyList()
        val possibleEnds = listOf("Station Progress", "I-Reported Cases")
            .map { fullText.indexOf(it, start, ignoreCase = true) }
            .filter { it >= 0 }
        val end = possibleEnds.minOrNull() ?: fullText.length
        val block = fullText.substring(start, end)
        val stationBlocks = mutableListOf<Pair<NormalizedStation, StringBuilder>>()
        block.lineSequence().forEach { rawLine ->
            val line = DsrNormalization.whitespace(rawLine)
            if (line.isBlank() || line.startsWith("Police Station", true)) return@forEach
            val startMatch = DsrNormalization.stationAtStart(line)
            if (startMatch != null) {
                stationBlocks += startMatch.first to StringBuilder(startMatch.second)
            } else if (stationBlocks.isNotEmpty()) {
                stationBlocks.last().second.append(' ').append(line)
            }
        }
        return stationBlocks.mapNotNull { (station, builder) ->
            val details = DsrNormalization.whitespace(builder.toString())
            if (details.isBlank() || details.equals("Nil", true)) return@mapNotNull null
            val crowd = Regex("(?i)(?:approximately|about|crowd\\s*[:=-]?)?\\s*([\\d,]+)\\s*(?:people|persons)")
                .find(details)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
            ParsedDsrForecast(
                stableKey = DsrNormalization.stableId(eventDate, station.code, details),
                eventDate = eventDate,
                stationCode = station.code,
                stationName = station.displayName,
                category = forecastCategory(details),
                priority = DsrNormalization.priorityForForecast(details, crowd),
                expectedCrowd = crowd,
                details = details,
            )
        }
    }

    private fun forecastCategory(details: String): String {
        val value = details.uppercase(Locale.ENGLISH)
        return when {
            value.contains("PROTEST") || value.contains("DEMONSTRATION") -> "PROTEST"
            value.contains("EXAM") -> "EXAM"
            value.contains("KABADDI") || value.contains("SPORT") -> "SPORTS"
            listOf("TEMPLE", "FESTIVAL", "PROCESSION", "KUMBABHISHEKAM").any(value::contains) -> "RELIGIOUS_EVENT"
            value.contains("CAMP") -> "PUBLIC_CAMP"
            else -> "OTHER_EVENT"
        }
    }

    private fun parseDsrMetrics(
        fullText: String,
        stations: List<ParsedStationSnapshot>,
    ): List<ParsedDsrMetric> {
        val metrics = mutableListOf<ParsedDsrMetric>()
        stations.firstOrNull { it.stationCode == "TOTAL" }?.let { total ->
            fun add(code: String, value: Int?) {
                if (value != null) metrics += ParsedDsrMetric(code, value, DsrMetricSemantics.DAILY)
            }
            add("dsr.reported", total.reportedCases)
            add("dsr.charged", total.chargedCases)
            add("dsr.other_disposal", total.otherDisposals)
            add("esummon.received", total.eSummonsReceived)
            add("esummon.served", total.eSummonsServed)
            add("esakshya.recorded", total.eSakshyaRecorded)
            add("esakshya.linked", total.eSakshyaLinked)
            add("mv.dd", total.mvDdCases)
            add("mv.other", total.mvOtherCases)
            add("court.taken_on_file", total.takenOnFile)
            add("court.convictions", total.convictions)
            add("court.acquittals", total.acquittals)
        }
        lastTotalValue(section(fullText, "e-Summon Statistical report", listOf("e-Sakhya Progress")))?.let {
            metrics += ParsedDsrMetric("esummon.pending", it, DsrMetricSemantics.PENDING)
        }
        totalValues(section(fullText, "e-Sakhya Progress", listOf("FRS Query", "Smart Kavalar", "MURDER, POCSO")))
            ?.takeIf { it.size >= 2 }?.let {
                metrics += ParsedDsrMetric("esakshya.pending", it[it.lastIndex - 1], DsrMetricSemantics.PENDING)
                metrics += ParsedDsrMetric("esakshya.sid_later", it.last(), DsrMetricSemantics.PENDING)
            }
        lastTotalValue(
            section(
                fullText,
                "NBW Pending",
                listOf("NBW Received", "C.No.", "e-filed rectification", "Case Note", "Station CCTV"),
            ),
        )?.let {
            metrics += ParsedDsrMetric("nbw.pending", it, DsrMetricSemantics.PENDING)
        }
        lastTotalValue(section(fullText, "e-filed rectification", listOf("Case Note", "Station CCTV", "MV Petty Case")))?.let {
            metrics += ParsedDsrMetric("efile_rectification.pending", it, DsrMetricSemantics.PENDING)
        }
        return metrics.distinctBy { it.metricCode }
    }

    private fun parseTasmac(
        originalFileName: String,
        pages: List<String>,
        fullText: String,
    ): ParsedOperationalReport {
        val reportDate = Regex("(?i)TASMAC\\s+DETAILS\\s*[-–]?\\s*(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})")
            .find(fullText)?.let(::dateFromMatch) ?: dateFromFileName(originalFileName)
        val issues = mutableListOf<ParsedDsrIssue>()
        val values = fullText.lineSequence()
            .map(DsrNormalization::whitespace)
            .firstOrNull { it.startsWith("SDO Total", true) }
            ?.let { Regex("\\d+").findAll(it.substringAfter("Total", "")).map { n -> n.value.toInt() }.toList() }
            .orEmpty()
        val metrics = if (values.size >= 5) {
            listOf(
                ParsedDsrMetric("tasmac.shops", values[0], DsrMetricSemantics.INVENTORY),
                ParsedDsrMetric("tasmac.cctv_available", values[1], DsrMetricSemantics.INVENTORY),
                ParsedDsrMetric("tasmac.cctv_working", values[2], DsrMetricSemantics.INVENTORY),
                ParsedDsrMetric("tasmac.patta_available", values[3], DsrMetricSemantics.INVENTORY),
                ParsedDsrMetric("tasmac.patta_missing", values[4], DsrMetricSemantics.INVENTORY),
            )
        } else {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "TASMAC_SUMMARY_UNREADABLE",
                "The TASMAC subdivision summary row could not be read.",
            )
            emptyList()
        }
        if (values.size >= 5 && values[3] + values[4] != values[0]) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "TASMAC_INVENTORY_SUM_MISMATCH",
                "Patta Book available and unavailable totals do not equal the number of TASMAC shops.",
            )
        }
        val detailedStatus = fullText.lineSequence()
            .map(DsrNormalization::whitespace)
            .filter { Regex("^\\d+[.)]?\\s+.*\\s(?:Yes|No)$", RegexOption.IGNORE_CASE).matches(it) }
            .map { it.substringAfterLast(' ').uppercase(Locale.ENGLISH) }
            .toList()
        if (values.size >= 5 && detailedStatus.size == values[0]) {
            val yes = detailedStatus.count { it == "YES" }
            val no = detailedStatus.count { it == "NO" }
            if (yes != values[3] || no != values[4]) {
                issues += ParsedDsrIssue(
                    DsrIssueSeverity.ERROR,
                    "TASMAC_DETAIL_SUMMARY_MISMATCH",
                    "The shop-level Patta Book statuses do not agree with the subdivision summary.",
                )
            }
        }
        return ParsedOperationalReport(
            reportType = OperationalReportType.TASMAC,
            reportDate = reportDate,
            pageCount = pages.size,
            metrics = metrics,
            issues = issues,
        )
    }

    private fun parseFatalAccidents(
        originalFileName: String,
        pages: List<String>,
        fullText: String,
    ): ParsedOperationalReport {
        val issues = mutableListOf<ParsedDsrIssue>()
        val titlePeriod = Regex("(?i)FATAL\\s+ACCIDENTS?\\s+OCCURRED\\s+IN\\s+([A-Z]+)\\s+(20\\d{2})")
            .find(fullText)
        val titleMonth = titlePeriod?.groupValues?.get(1)?.let(DsrNormalization::monthNumber)
        val titleYear = titlePeriod?.groupValues?.get(2)?.toIntOrNull()
        val occurrenceDates = genericDate.findAll(fullText)
            .mapNotNull { match ->
                val day = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val month = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val yearRaw = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
                DsrNormalization.isoDate(day, month, yearRaw)?.let(LocalDate::parse)
            }
            .toList()
        val modalMonth = occurrenceDates.groupingBy { it.monthValue }.eachCount().maxByOrNull { it.value }?.key
        val modalYear = occurrenceDates.groupingBy { it.year }.eachCount().maxByOrNull { it.value }?.key
        if (titleMonth != null && modalMonth != null && titleMonth != modalMonth) {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.ERROR,
                "ACCIDENT_PERIOD_MISMATCH",
                "The fatal-accident title month does not match the month recorded in most case dates.",
            )
        }
        val accidentCounts = Regex("(?i)Total\\s+No\\.?\\s+of\\s+Accident\\s*:\\s*(\\d+)")
            .findAll(fullText).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        val metrics = if (accidentCounts.isNotEmpty()) {
            listOf(ParsedDsrMetric("traffic.fatal_accident_cases", accidentCounts.sum(), DsrMetricSemantics.CUMULATIVE))
        } else {
            issues += ParsedDsrIssue(
                DsrIssueSeverity.WARNING,
                "FATAL_ACCIDENT_TOTAL_UNREADABLE",
                "The fatal-accident category totals could not be read.",
            )
            emptyList()
        }
        val reportDate = if (modalMonth != null && modalYear != null) {
            LocalDate.of(modalYear, modalMonth, 1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } else {
            dateFromFileName(originalFileName)
        }
        return ParsedOperationalReport(
            reportType = OperationalReportType.FATAL_ACCIDENT,
            reportDate = reportDate,
            pageCount = pages.size,
            metrics = metrics,
            issues = issues,
        )
    }

    private fun totalValues(block: String?): List<Int>? {
        val totalLine = block?.lineSequence()?.map(DsrNormalization::whitespace)
            ?.lastOrNull { it.startsWith("Total", true) || it.startsWith("SDO Total", true) }
            ?: return null
        return Regex("(?<![A-Za-z])\\d+(?![A-Za-z])").findAll(totalLine.substringAfter("Total", ""))
            .map { it.value.toInt() }.toList()
    }

    private fun lastTotalValue(block: String?): Int? = totalValues(block)?.lastOrNull()

    private fun section(text: String, startLabel: String, endLabels: List<String>): String? {
        val start = text.indexOf(startLabel, ignoreCase = true)
        if (start < 0) return null
        val contentStart = start + startLabel.length
        val end = endLabels.map { text.indexOf(it, contentStart, ignoreCase = true) }
            .filter { it >= 0 }
            .minOrNull() ?: text.length
        return text.substring(contentStart, end)
    }

    private fun dateFromFileName(fileName: String): String? = genericDate.find(fileName)?.let(::dateFromMatch)

    private fun dateFromMatch(match: MatchResult?): String? {
        if (match == null || match.groupValues.size < 4) return null
        return DsrNormalization.isoDate(
            match.groupValues[1].toIntOrNull() ?: return null,
            match.groupValues[2].toIntOrNull() ?: return null,
            match.groupValues[3].toIntOrNull() ?: return null,
        )
    }
}
