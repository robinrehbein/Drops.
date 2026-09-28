package de.birneklub.drop.core.domain

/** Checks the sign-in form before it reaches the server; messages say how to fix the field. */
object AccountInput {
    enum class Field { SERVER, EMAIL, PASSWORD }

    const val MIN_PASSWORD = 8
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun errors(server: String, mail: String, password: String): Map<Field, String> = buildMap {
        val url = server.trim()
        if (!url.startsWith("https://") || url.removePrefix("https://").substringBefore('/').length < 3) {
            put(Field.SERVER, "Serveradresse mit https:// eintragen, z. B. https://drops.example.de")
        }
        if (!email.matches(mail.trim())) put(Field.EMAIL, "E-Mail-Adresse eintragen, z. B. name@beispiel.de")
        if (password.length < MIN_PASSWORD) put(Field.PASSWORD, "Mindestens $MIN_PASSWORD Zeichen.")
    }
}
