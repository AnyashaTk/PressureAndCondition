package com.local.statetracker.ocr

data class ParsedPressure(val systolic:Int?=null,val diastolic:Int?=null,val pulse:Int?=null)

class BloodPressureParser {
    fun parse(text:String):ParsedPressure {
        fun labeled(vararg labels:String):Int? { val names=labels.joinToString("|"){Regex.escape(it)};return Regex("(?i)(?:$names)\\s*[:=-]?\\s*(\\d{2,3})").find(text)?.groupValues?.get(1)?.toIntOrNull() }
        val sys=labeled("SYS","SYSTOLIC","СИСТ")
        val dia=labeled("DIA","DIASTOLIC","ДИА")
        val pulse=labeled("PUL","PULSE","ПУЛЬС")
        if(sys!=null||dia!=null||pulse!=null)return ParsedPressure(sys,dia,pulse)
        val fraction=Regex("(?<!\\d)(\\d{2,3})\\s*[/\\\\]\\s*(\\d{2,3})(?!\\d)").find(text)
        if(fraction!=null)return ParsedPressure(fraction.groupValues[1].toIntOrNull(),fraction.groupValues[2].toIntOrNull(),null)
        return ParsedPressure()
    }
}
