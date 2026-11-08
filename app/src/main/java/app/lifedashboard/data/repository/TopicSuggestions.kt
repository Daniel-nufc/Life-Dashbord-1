package app.lifedashboard.data.repository

/**
 * Themen-Vorschläge für Klausuren ohne eigene Themen – typische Inhalte der Einführungsphase (11)
 * nach dem Berliner Rahmenlehrplan als Startpunkt. In der App jederzeit änderbar.
 */
object TopicSuggestions {
    private val byCode = mapOf(
        "MA" to listOf("Funktionen & Graphen", "Lineare und quadratische Funktionen", "Potenz- und Exponentialfunktionen", "Änderungsrate & Ableitung", "Kurvendiskussion", "Übungsaufgaben / Probeklausur"),
        "DE" to listOf("Erörterung", "Sachtextanalyse", "Gedichtinterpretation", "Epik / Kurzgeschichte", "Fachbegriffe & Stilmittel", "Probeaufsatz schreiben"),
        "E" to listOf("Reading comprehension", "Writing: comment / essay", "Mediation", "Grammar (tenses, conditionals)", "Vocabulary zum Unterrichtsthema", "Listening"),
        "BIO" to listOf("Zellbiologie", "Biomembran & Stofftransport", "Enzyme", "Stoffwechsel", "Genetik-Grundlagen", "Fachbegriffe & Abbildungen"),
        "CH" to listOf("Atombau & Periodensystem", "Chemische Bindung", "Stoffmenge & Stöchiometrie", "Säuren & Basen", "Redoxreaktionen", "Rechenaufgaben üben"),
        "GEWI" to listOf("Politisches System Deutschlands", "Demokratie & Wahlen", "Wirtschaftsordnung", "Quellen- und Textanalyse", "Fachbegriffe", "Aktuelle Beispiele"),
        "PM" to listOf("Projektphasen", "Projektplanung (Ziele, Meilensteine)", "Zeit- & Ressourcenplanung", "Methoden & Werkzeuge", "Fallbeispiel durchgehen"),
        "TUMM" to listOf("Grundbegriffe Management", "Unternehmensziele & -organisation", "Methoden aus dem Unterricht", "Fallbeispiel / Aufgaben"),
        "TUMT" to listOf("Technische Grundbegriffe", "Zeichnungen & Berechnungen", "Methoden aus dem Unterricht", "Übungsaufgaben"),
    )
    private val generic = listOf("Unterrichtsmitschriften wiederholen", "Fachbegriffe lernen", "Übungsaufgaben", "Probeklausur / alte Aufgaben")

    fun forSubject(code: String, name: String = ""): List<String> {
        val key = code.substringBefore('-').uppercase().replace("BIO-PRO", "BIO")
        return byCode[key] ?: byCode.entries.firstOrNull { name.uppercase().startsWith(it.key) }?.value ?: generic
    }
}
