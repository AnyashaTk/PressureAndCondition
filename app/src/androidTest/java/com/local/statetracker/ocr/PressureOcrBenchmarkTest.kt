package com.local.statetracker.ocr

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PressureOcrBenchmarkTest {
    @Test
    fun dumpFullLatinStructureForPressureV1() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixtureAssets = instrumentation.context.assets
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val fixtureNames = fixtureAssets.open("test_pressures.csv").bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { it.substringBefore(',').trim() }.take(5).toList()
        }
        val dump = StringBuilder()
        fun emit(message: String) {
            Log.i(STRUCTURE_TAG, message)
            dump.appendLine(message)
        }

        fixtureNames.forEach { fixtureName ->
            val imageFile = File(context.cacheDir, "structure-$fixtureName")
            fixtureAssets.open(fixtureName).use { input ->
                imageFile.outputStream().use(input::copyTo)
            }
            val result = Tasks.await(
                recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(imageFile))),
                60,
                TimeUnit.SECONDS,
            )
            emit("IMAGE $fixtureName RAW=${result.text.replace("\n", "\\n")}")
            result.textBlocks.forEachIndexed { blockIndex, block ->
                emit("IMAGE $fixtureName BLOCK[$blockIndex] text=${block.text.replace("\n", "\\n")} box=${block.boundingBox}")
                block.lines.forEachIndexed { lineIndex, line ->
                    emit("IMAGE $fixtureName BLOCK[$blockIndex] LINE[$lineIndex] text=${line.text} box=${line.boundingBox}")
                    line.elements.forEachIndexed { elementIndex, element ->
                        emit("IMAGE $fixtureName BLOCK[$blockIndex] LINE[$lineIndex] ELEMENT[$elementIndex] text=${element.text} box=${element.boundingBox} confidence=${element.confidence}")
                        element.symbols.forEachIndexed { symbolIndex, symbol ->
                            emit("IMAGE $fixtureName BLOCK[$blockIndex] LINE[$lineIndex] ELEMENT[$elementIndex] SYMBOL[$symbolIndex] text=${symbol.text} box=${symbol.boundingBox} confidence=${symbol.confidence}")
                        }
                    }
                }
            }
            emit("IMAGE $fixtureName END blocks=${result.textBlocks.size}")
            imageFile.delete()
        }
        File(context.getExternalFilesDir(null), "pressure-v1-mlkit-structure.txt").writeText(dump.toString())
        recognizer.close()
        assertTrue("Verifier did not process every pressure-v1 fixture", fixtureNames.size == 5)
    }

    @Test
    fun reportProductionBaselineForPressureV1() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixtureAssets = instrumentation.context.assets
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val moduleClient=ModuleInstall.getClient(context)
        val availability=Tasks.await(moduleClient.areModulesAvailable(chineseRecognizer),60,TimeUnit.SECONDS)
        if(!availability.areModulesAvailable())Tasks.await(moduleClient.installModules(ModuleInstallRequest.newBuilder().addApi(chineseRecognizer).build()),180,TimeUnit.SECONDS)
        var modelReady=Tasks.await(moduleClient.areModulesAvailable(chineseRecognizer),60,TimeUnit.SECONDS).areModulesAvailable()
        repeat(30){if(!modelReady){Thread.sleep(2_000);modelReady=Tasks.await(moduleClient.areModulesAvailable(chineseRecognizer),60,TimeUnit.SECONDS).areModulesAvailable()}}
        assertTrue("Chinese OCR optional module was not installed before measurement",modelReady)
        val expected = fixtureAssets.open("test_pressures.csv").bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { row ->
                val columns = row.split(',').map(String::trim)
                Expected(columns[0], columns[1].toInt(), columns[2].toInt(), columns[3].toInt())
            }.toList()
        }
        var correctFields = 0
        var completeTriplets = 0
        var usableSysDia = 0
        var nonEmptyText = 0
        var structuredCorrect=0;var structuredComplete=0;var structuredUsable=0
        var chineseCorrect=0;var chineseComplete=0;var chineseUsable=0;var chineseNonEmpty=0
        expected.forEach { fixture ->
            val imageFile = File(context.cacheDir, "benchmark-${fixture.name}")
            fixtureAssets.open(fixture.name).use { input -> imageFile.outputStream().use(input::copyTo) }
            val result = Tasks.await(
                recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(imageFile))),
                60,
                TimeUnit.SECONDS,
            )
            val chineseResult=Tasks.await(chineseRecognizer.process(InputImage.fromFilePath(context,Uri.fromFile(imageFile))),60,TimeUnit.SECONDS)
            val parsed = BloodPressureParser().parse(result.text)
            val latinElements=result.textBlocks.flatMap{it.lines}.flatMap{it.elements}.mapNotNull{e->e.boundingBox?.let{b->OcrElement(e.text,OcrBounds(b.left,b.top,b.right,b.bottom))}}
            val structuredParsed=BloodPressureParser().parse(result.text,latinElements)
            val chineseElements=chineseResult.textBlocks.flatMap{it.lines}.flatMap{it.elements}.mapNotNull{e->e.boundingBox?.let{b->OcrElement(e.text,OcrBounds(b.left,b.top,b.right,b.bottom))}}
            val chineseParsed=BloodPressureParser().parse(chineseResult.text,chineseElements)
            val matches = listOf(
                parsed.systolic == fixture.sys,
                parsed.diastolic == fixture.dia,
                parsed.pulse == fixture.pul,
            ).count { it }
            correctFields += matches
            if (matches == 3) completeTriplets++
            if (parsed.systolic != null && parsed.diastolic != null) usableSysDia++
            if (result.text.isNotBlank()) nonEmptyText++
            fun matches(p:ParsedPressure)=listOf(p.systolic==fixture.sys,p.diastolic==fixture.dia,p.pulse==fixture.pul).count{it}
            val structuredMatches=matches(structuredParsed);structuredCorrect+=structuredMatches;if(structuredMatches==3)structuredComplete++;if(structuredParsed.systolic!=null&&structuredParsed.diastolic!=null)structuredUsable++
            val chineseMatches=matches(chineseParsed);chineseCorrect+=chineseMatches;if(chineseMatches==3)chineseComplete++;if(chineseParsed.systolic!=null&&chineseParsed.diastolic!=null)chineseUsable++;if(chineseResult.text.isNotBlank())chineseNonEmpty++
            val structured = result.textBlocks.joinToString(" | ") { block ->
                block.lines.joinToString(" / ") { line ->
                    line.elements.joinToString(" ") { element -> "${element.text}@${element.boundingBox}" }
                }
            }
            Log.i(TAG, "${fixture.name} expected=${fixture.sys}/${fixture.dia}/${fixture.pul} predicted=${parsed.systolic}/${parsed.diastolic}/${parsed.pulse} matches=$matches raw=${result.text.replace('\n', '|')} structured=$structured")
            Log.i(TAG, "${fixture.name} chinese=${chineseResult.text.replace('\n', '|')}")
            Log.i(TAG,"${fixture.name} structured_expected=${fixture.sys}/${fixture.dia}/${fixture.pul} latin_predicted=${structuredParsed.systolic}/${structuredParsed.diastolic}/${structuredParsed.pulse} latin_matches=$structuredMatches chinese_predicted=${chineseParsed.systolic}/${chineseParsed.diastolic}/${chineseParsed.pulse} chinese_matches=$chineseMatches")
            imageFile.delete()
        }
        Log.i(TAG, "SUMMARY correct_fields=$correctFields/15 accuracy=${"%.1f".format(correctFields / 15.0 * 100)} complete_triplets=$completeTriplets/5 usable_sys_dia=$usableSysDia/5 non_empty_text=$nonEmptyText/5")
        Log.i(TAG,"STRUCTURED_SUMMARY latin=$structuredCorrect/15 accuracy=${"%.1f".format(structuredCorrect/15.0*100)} complete=$structuredComplete/5 usable=$structuredUsable/5 empty=${5-nonEmptyText}/5 chinese=$chineseCorrect/15 accuracy=${"%.1f".format(chineseCorrect/15.0*100)} complete=$chineseComplete/5 usable=$chineseUsable/5 empty=${5-chineseNonEmpty}/5")
        recognizer.close()
        chineseRecognizer.close()
        assertTrue("Verifier did not process every fixture", expected.size == 5)
    }

    private data class Expected(val name: String, val sys: Int, val dia: Int, val pul: Int)


    private companion object {
        const val TAG = "PressureOcrBenchmark"
        const val STRUCTURE_TAG = "PressureOcrStructure"
    }
}
