package org.aionlink.vehicles
data class VehicleProfile(
    val manufacturer: String, val brand: String, val model: String,
    val variant: String, val headUnit: String, val firmware: String,
    val protocolCandidate: String, val verifiedTransports: Set<String> = emptySet(),
    val verifiedFeatures: Set<String> = emptySet(), val evidence: List<String> = emptyList()
) { val compatibility = "NOT YET VERIFIED" }
object VehicleProfiles {
    val aionI60 = VehicleProfile("GAC", "AION", "i60", "REEV Platinum", "ADiGO",
        "GAC_T51_IDC_S003_260310_0742921_R", "ICCOA CarLink")
}
