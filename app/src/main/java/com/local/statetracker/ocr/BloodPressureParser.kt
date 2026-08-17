package com.local.statetracker.ocr

data class ParsedPressure(val systolic:Int?=null,val diastolic:Int?=null,val pulse:Int?=null)

class BloodPressureParser {
    fun parse(text:String):ParsedPressure {
        if(Regex("(?<!\\d)\\d{4}(?!\\d)").containsMatchIn(text))return ParsedPressure()
        val values=Regex("(?<!\\d)\\d{2,3}(?!\\d)").findAll(text).mapNotNull{it.value.toIntOrNull()}.take(3).toList()
        return ParsedPressure(values.getOrNull(0),values.getOrNull(1),values.getOrNull(2))
    }
}
