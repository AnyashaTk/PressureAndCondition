package com.local.statetracker.ocr

data class ParsedPressure(val systolic:Int?=null,val diastolic:Int?=null,val pulse:Int?=null)
data class OcrBounds(val left:Int,val top:Int,val right:Int,val bottom:Int){fun centerY()=(top+bottom)/2}
data class OcrElement(val text:String,val bounds:OcrBounds)
data class PressureDraft(val systolic:String="",val diastolic:String="",val pulse:String="")
object OcrDraftMapper{
    fun prefill(parsed:ParsedPressure)=PressureDraft(parsed.systolic?.toString().orEmpty(),parsed.diastolic?.toString().orEmpty(),parsed.pulse?.toString().orEmpty())
    fun cameraCancelled(current:PressureDraft)=current
}

class BloodPressureParser {
    fun parse(text:String):ParsedPressure {
        if(Regex("(?<!\\d)\\d{4}(?!\\d)").containsMatchIn(text))return ParsedPressure()
        val values=Regex("(?<!\\d)\\d{2,3}(?!\\d)").findAll(text).mapNotNull{it.value.toIntOrNull()}.take(3).toList()
        return ParsedPressure(values.getOrNull(0),values.getOrNull(1),values.getOrNull(2))
    }

    fun parse(text:String,elements:List<OcrElement>):ParsedPressure {
        val labels=elements.mapNotNull{e->val key=e.text.uppercase().filter(Char::isLetter).let{when{it.startsWith("SYS")->"SYS";it.startsWith("DIA")->"DIA";it.startsWith("PUL")->"PUL";else->null}};key?.let{it to e.bounds.centerY()}}.toMap()
        val assigned=mutableMapOf<String,Int>()
        elements.forEach{e->Regex("(?<!\\d)\\d{2,3}(?!\\d)").findAll(e.text).forEach{match->val value=match.value.toIntOrNull()?:return@forEach;val nearest=labels.minByOrNull{kotlin.math.abs(it.value-e.bounds.centerY())};if(nearest!=null&&nearest.key !in assigned)assigned[nearest.key]=value}}
        fun labeled(vararg names:String):Int?{val pattern=names.joinToString("|"){Regex.escape(it)};return Regex("(?i)(?:$pattern)\\s*[:=-]?\\s*(\\d{2,3})").find(text)?.groupValues?.get(1)?.toIntOrNull()}
        val fallback=ParsedPressure(assigned["SYS"]?:labeled("SYS","SYSTOLIC","СИСТ"),assigned["DIA"]?:labeled("DIA","DIASTOLIC","ДИА"),assigned["PUL"]?:labeled("PUL","PULSE","ПУЛЬС"))
        if(fallback.systolic!=null||fallback.diastolic!=null||fallback.pulse!=null)return fallback
        val fraction=Regex("(?<!\\d)(\\d{2,3})\\s*[/\\\\]\\s*(\\d{2,3})(?!\\d)").find(text)
        return if(fraction!=null)ParsedPressure(fraction.groupValues[1].toIntOrNull(),fraction.groupValues[2].toIntOrNull()) else ParsedPressure()
    }
}
