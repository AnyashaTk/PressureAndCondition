package com.local.statetracker.ocr

import org.junit.Assert.*
import org.junit.Test

class BloodPressureParserTest {
    private val parser=BloodPressureParser()
    @Test fun labeledValues(){assertEquals(ParsedPressure(118,76,68),parser.parse("SYS 118 DIA 76 PULSE 68"))}
    @Test fun pulseMayBeMissing(){assertEquals(ParsedPressure(120,80,null),parser.parse("120/80"))}
    @Test fun unrelatedNumbersAreNotGuessed(){assertEquals(ParsedPressure(),parser.parse("2026 08 17 14:03"))}
    @Test fun arbitraryTextNeverCrashes(){assertEquals(ParsedPressure(),parser.parse("!? пусто 🫀"))}
    @Test fun threeValuesFollowReadingOrder(){assertEquals(ParsedPressure(118,76,68),parser.parse("118 76 68"))}
    @Test fun twoValuesLeavePulseMissing(){assertEquals(ParsedPressure(118,76,null),parser.parse("118 76"))}
    @Test fun oneValueLeavesOthersMissing(){assertEquals(ParsedPressure(118,null,null),parser.parse("118"))}
    @Test fun cameraAndGalleryOcrPrefillEditableDraft(){assertEquals(PressureDraft("118","76","68"),OcrDraftMapper.prefill(parser.parse("118 76 68")))}
    @Test fun cameraCancelPreservesExistingFields(){val current=PressureDraft("120","80","70");assertSame(current,OcrDraftMapper.cameraCancelled(current))}
}
