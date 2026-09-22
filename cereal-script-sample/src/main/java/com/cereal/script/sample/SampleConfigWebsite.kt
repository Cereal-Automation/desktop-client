package com.cereal.script.sample

enum class SampleConfigWebsite(
    val text: String,
) {
    FIRST_OPTION("Option 1"),
    SECOND_OPTION("Option 2"),
    THIRD_OPTION("Option 3"),
    FOURTH_OPTION("Option 4"),
    FIFTH_OPTION("Option 5"),
    ;

    override fun toString(): String = text
}
