package app.nabd.nativeapp.data

/**
 * Owner/admin detection. Accepts `f0uri`, `@f0uri`, `f0uri@`, extra spaces and
 * any letter case, exactly as specified for the Nabd admin account.
 */
fun isAdminHandle(handle: String?): Boolean = handle?.replace("@", "").trim().lowercase() == "f0uri"
