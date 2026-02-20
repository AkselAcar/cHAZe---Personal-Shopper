package com.epfl.esl.chaze.data.model

/**
 * Enum class representing different transportation modes for route optimization and navigation.
 */
enum class TransportMode(val displayName: String, val mapsApiValue: String) {
    DRIVING("Driving", "driving"),
    WALKING("Walking", "walking"),
    BICYCLING("Bicycling", "bicycling"),
    TRANSIT("Public Transport", "transit");
    
    companion object {
        /**
         * Parse a TransportMode from its string name (e.g., "DRIVING").
         * 
         * This function enables conversion from stored string values (Firebase, preferences)
         * back to the enum type. If the provided value doesn't match any enum entry,
         * it defaults to DRIVING as the fallback mode.
         *
         * Parameters:
         * value (String): The enum name to parse (case-sensitive)
         *
         * Returns:
         * TransportMode: The corresponding enum value, or DRIVING if not found
         */
        fun fromString(value: String): TransportMode {
            return entries.find { it.name == value } ?: DRIVING
        }
    }
}
