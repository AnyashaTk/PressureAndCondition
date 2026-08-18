package com.local.statetracker

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.local.statetracker.data.*
import com.local.statetracker.export.CsvExporter
import com.local.statetracker.health.AndroidHealthConnectDataSource
import com.local.statetracker.notifications.EXTRA_DATE
import com.local.statetracker.notifications.EXTRA_SLOT
import com.local.statetracker.notifications.ReminderTime
import com.local.statetracker.ocr.BloodPressureParser
import com.local.statetracker.ocr.OcrDraftMapper
import com.local.statetracker.ocr.OcrElement
import com.local.statetracker.ocr.OcrBounds
import com.local.statetracker.settings.ThemeMode
import com.local.statetracker.ui.MascotGarden
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.io.File

class MainActivity:ComponentActivity(){
    override fun onCreate(state:Bundle?){super.onCreate(state);enableEdgeToEdge();val date=intent.getStringExtra(EXTRA_DATE);val slot=intent.getStringExtra(EXTRA_SLOT);setContent{val vm:MainViewModel=viewModel();val mode by vm.themeMode.collectAsState();StateTheme(mode){StateApp(date,slot,vm)}}}
}

@Composable fun CalendarScreen(nav:NavController,vm:MainViewModel){
    var month by remember{mutableStateOf(YearMonth.now())}
    val all by vm.all.collectAsState();val pressure by vm.pressure.collectAsState();val cycles by vm.cycles.collectAsState()
    Page("Календарь"){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick={month=month.minusMonths(1)}){Icon(Icons.Default.ChevronLeft,"Предыдущий месяц")}
            Text("${month.month.getDisplayName(TextStyle.FULL_STANDALONE,Locale("ru"))} ${month.year}",style=MaterialTheme.typography.titleLarge)
            IconButton(onClick={month=month.plusMonths(1)},enabled=month<YearMonth.now()){Icon(Icons.Default.ChevronRight,"Следующий месяц")}
        }
        Row(Modifier.fillMaxWidth()){listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс").forEach{Text(it,Modifier.weight(1f),style=MaterialTheme.typography.labelSmall)}}
        val cells=List(month.atDay(1).dayOfWeek.value-1){null}+(1..month.lengthOfMonth()).map{month.atDay(it)}
        cells.chunked(7).forEach{week->
            Row(Modifier.fillMaxWidth()){
                week.forEach{date->
                    if(date==null) Spacer(Modifier.weight(1f).height(58.dp))
                    else {
                        val entries=all.filter{it.checkIn.targetDate==date.toString()}
                        val hasP=pressure.any{Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDate()==date}
                        val hasC=cycles.any{it.date==date.toString()}
                        Surface(onClick={nav.navigate("day/$date")},enabled=date<=LocalDate.now(),shape=RoundedCornerShape(8.dp),color=if(date==LocalDate.now())MaterialTheme.colorScheme.primaryContainer else Color.Transparent,modifier=Modifier.weight(1f).height(58.dp).padding(2.dp)){
                            Column(Modifier.padding(5.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(date.dayOfMonth.toString());val start=cycles.map{LocalDate.parse(it.date)}.filter{it<=date}.maxOrNull();start?.let{Text((java.time.temporal.ChronoUnit.DAYS.between(it,date)+1).toString(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}};Row{if(entries.any{it.checkIn.slot==CheckInSlot.DAY})Dot(Color(0xFF4E79A7));if(entries.any{it.checkIn.slot==CheckInSlot.EVENING})Dot(Color(0xFFF28E2B));if(hasP)Dot(Color(0xFFE15759));if(hasC)Dot(Color(0xFFB07AA1))}}
                        }
                    }
                }
                repeat(7-week.size){Spacer(Modifier.weight(1f))}
            }
        }
        Text("● День   ● Вечер   ● Давление   ● Начало цикла",style=MaterialTheme.typography.bodySmall)
    }
}
@Composable fun Dot(color:Color){Box(Modifier.padding(1.dp).size(6.dp).background(color,RoundedCornerShape(50)))}

@Composable fun DayScreen(nav:NavController,vm:MainViewModel,date:LocalDate){val all by vm.all.collectAsState();val pressure by vm.pressure.collectAsState();val cycles by vm.cycles.collectAsState();val metrics by vm.metrics.collectAsState();val entries=all.filter{it.checkIn.targetDate==date.toString()};val cycle=cycles.any{it.date==date.toString()};Page(pretty(date)){
    listOf(CheckInSlot.DAY to "День",CheckInSlot.EVENING to "Вечер").forEach{(slot,label)->val e=entries.find{it.checkIn.slot==slot};Card(onClick={nav.navigate(if(e==null)"checkin/new/$date/$slot" else "checkin/${e.checkIn.id}")},Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(label,style=MaterialTheme.typography.titleMedium);if(e==null)Text("Добавить")else{Text(Instant.ofEpochMilli(e.checkIn.reportedForAt).atZone(ZoneId.systemDefault()).toLocalTime().toString());e.observations.take(4).forEach{o->Text("${metrics.find{it.id==o.metricId}?.displayName}: ${o.numericValue?.toInt()?:if(o.booleanValue==true)"Да" else "Нет"}")}}}}}
    entries.filter{it.checkIn.slot==CheckInSlot.EXTRA}.forEachIndexed{i,e->OutlinedCard(onClick={nav.navigate("checkin/${e.checkIn.id}")},Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("Дополнительное состояние ${i+1}");Text(Instant.ofEpochMilli(e.checkIn.reportedForAt).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))}}}
    Button(onClick={nav.navigate("checkin/new/$date/${CheckInSlot.EXTRA}")},Modifier.fillMaxWidth()){Text("+ Дополнительное")};OutlinedButton(onClick={nav.navigate("pressure")},Modifier.fillMaxWidth()){Text("+ Давление")}
    pressure.filter{Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDate()==date}.forEach{Text("Давление ${it.systolic} / ${it.diastolic} · ${Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}")}
    OutlinedButton(onClick={vm.toggleCycle(date,cycle)},Modifier.fillMaxWidth()){Text(if(cycle)"Удалить отметку начала цикла" else "Отметить начало цикла")}
}}

@Composable fun PressureScreen(nav:NavController,vm:MainViewModel,checkinId:String?,measurementId:String?){var sys by remember{mutableStateOf("")};var dia by remember{mutableStateOf("")};var pulse by remember{mutableStateOf("")};var source by remember{mutableStateOf(PressureSource.MANUAL)};var loading by remember{mutableStateOf(measurementId!=null)};var ocrMessage by remember{mutableStateOf<String?>(null)};val context=LocalContext.current
    LaunchedEffect(measurementId){measurementId?.let{vm.findPressure(it)}?.also{sys=it.systolic.toString();dia=it.diastolic.toString();pulse=it.pulse?.toString().orEmpty();source=it.source};loading=false}
    var cameraFile by remember{mutableStateOf<File?>(null)}
    fun recognize(uri:Uri,temporary:File?=null){loading=true;TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(context,uri)).addOnSuccessListener{text->val elements=text.textBlocks.flatMap{it.lines}.flatMap{it.elements}.mapNotNull{e->e.boundingBox?.let{b->OcrElement(e.text,OcrBounds(b.left,b.top,b.right,b.bottom))}};val p=BloodPressureParser().parse(text.text,elements);val draft=OcrDraftMapper.prefill(p);sys=draft.systolic;dia=draft.diastolic;pulse=draft.pulse;ocrMessage=if(p.systolic==null||p.diastolic==null)"Не удалось уверенно распознать все значения. Проверь поля и введи недостающие вручную." else null;source=PressureSource.OCR}.addOnFailureListener{ocrMessage="Не удалось распознать изображение. Введи значения вручную."}.addOnCompleteListener{loading=false;temporary?.delete();if(cameraFile==temporary)cameraFile=null}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri:Uri?->if(uri!=null)recognize(uri)}
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){captured->val file=cameraFile;if(captured&&file!=null)recognize(FileProvider.getUriForFile(context,"${context.packageName}.files",file),file)else{file?.delete();cameraFile=null}}
    DisposableEffect(Unit){onDispose{cameraFile?.delete()}}
    Page(if(measurementId==null)"Давление" else "Изменить давление"){
        if(loading)CircularProgressIndicator();Button(onClick={runCatching{val file=File.createTempFile("bp-camera-",".jpg",context.cacheDir);cameraFile=file;camera.launch(FileProvider.getUriForFile(context,"${context.packageName}.files",file))}.onFailure{cameraFile?.delete();cameraFile=null;ocrMessage="Не удалось открыть камеру. Можно выбрать фотографию или ввести значения вручную."}},Modifier.fillMaxWidth()){Icon(Icons.Default.PhotoCamera,null);Text(" Сфотографировать")};OutlinedButton(onClick={picker.launch("image/*")},Modifier.fillMaxWidth()){Text("Выбрать фото")}
        OutlinedTextField(sys,{sys=it},label={Text("Верхнее / SYS")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(dia,{dia=it},label={Text("Нижнее / DIA")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(pulse,{pulse=it},label={Text("Пульс")},modifier=Modifier.fillMaxWidth())
        ocrMessage?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={vm.savePressure(measurementId,sys.toIntOrNull(),dia.toIntOrNull(),pulse.toIntOrNull(),source,checkinId){nav.popBackStack()}},Modifier.fillMaxWidth()){Text("Сохранить")}
    }}

data class ChartSeries(val label:String,val color:Color,val points:List<Pair<Float,Float>>,val bucketValues:Map<Int,List<Float>>)
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AnalyticsScreen(vm:MainViewModel){val all by vm.all.collectAsState();val pressure by vm.pressure.collectAsState();val metrics by vm.metrics.collectAsState();val cycles by vm.cycles.collectAsState();val showCycles by vm.showCycleOverlay.collectAsState();var days by remember{mutableIntStateOf(30)};var selected by remember{mutableStateOf(setOf("energy","anxiety"))};var slots by remember{mutableStateOf(setOf(CheckInSlot.DAY,CheckInSlot.EVENING))};var grouping by remember{mutableStateOf(Grouping.RAW)};var aggregation by remember{mutableStateOf(PressureAggregation.MEAN)};var picker by remember{mutableStateOf(false)};var advanced by remember{mutableStateOf(false)};var legend by remember{mutableStateOf(true)};var selectedBucket by remember{mutableIntStateOf(-1)}
    if(picker)ModalBottomSheet(onDismissRequest={picker=false}){Column(Modifier.padding(20.dp).padding(bottom=40.dp)){Text("Метрики",style=MaterialTheme.typography.headlineSmall);metrics.forEach{m->Row(Modifier.fillMaxWidth().clickable{selected=if(m.id in selected)selected-m.id else selected+m.id},verticalAlignment=Alignment.CenterVertically){Checkbox(m.id in selected,null);Text(m.displayName)}}}}
    Page("Графики"){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){metrics.filter{it.id in selected}.take(2).forEach{AssistChip({},label={Text(it.displayName)})};if(selected.size>2)AssistChip({},label={Text("+${selected.size-2}")});TextButton(onClick={picker=true}){Text("Изменить")}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(7,30,90).forEach{d->FilterChip(days==d,{days=d},{Text(if(d==90)"3 мес." else "$d дней")})};TextButton(onClick={advanced=!advanced}){Text("Настройки")}}
        if(advanced)Card{Column(Modifier.padding(12.dp)){Text("Группировка");Row{Grouping.entries.forEach{g->FilterChip(grouping==g,{grouping=g;selectedBucket=-1},{Text(when(g){Grouping.RAW->"Raw";Grouping.DAY->"Дни";Grouping.WEEK->"Недели";Grouping.MONTH->"Месяцы"})})}};Text("Слоты");Row{CheckInSlot.entries.forEach{s->FilterChip(s in slots,{slots=if(s in slots)slots-s else slots+s},{Text(slotName(s))})}};Row(verticalAlignment=Alignment.CenterVertically){Switch(legend,{legend=it});Text("Показывать легенду")};if(grouping!=Grouping.RAW){Text("Агрегация давления");Row{PressureAggregation.entries.forEach{a->FilterChip(aggregation==a,{aggregation=a},{Text(a.ru)})}}}}}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Switch(showCycles,{vm.setCycleOverlay(it)});Text("Показывать начало цикла")}
        val rangeEnd=LocalDate.now();val rangeStart=rangeEnd.minusDays(days.toLong()-1);val domain=remember(rangeStart,rangeEnd,grouping){TimeDomain.buckets(rangeStart,rangeEnd,grouping)};val cycleDates=CycleOverlay.events(domain,cycles.map{LocalDate.parse(it.date)},showCycles).filter{it in rangeStart..rangeEnd};val slotColors=mapOf(CheckInSlot.DAY to Color(0xFF4E795E),CheckInSlot.EVENING to Color(0xFFD86B83),CheckInSlot.EXTRA to Color(0xFF8064A2))
        fun position(date:LocalDate,time:LocalTime?):Float{val i=TimeDomain.indexOf(domain,date);if(i<0)return -1f;return if(grouping==Grouping.RAW)i+(time?.toSecondOfDay()?:43200)/86400f else i+.5f}
        metrics.filter{it.id in selected}.forEach{m->val series=slots.map{slot->val samples=all.filter{it.checkIn.slot==slot}.mapNotNull{c->val date=LocalDate.parse(c.checkIn.targetDate);if(date !in rangeStart..rangeEnd)return@mapNotNull null;val i=TimeDomain.indexOf(domain,date);if(i<0)return@mapNotNull null;val o=c.observations.find{it.metricId==m.id}?:return@mapNotNull null;val value=(o.numericValue?:o.booleanValue?.let{b->if(b)1.0 else 0.0})?.toFloat()?:return@mapNotNull null;Triple(i,position(date,Instant.ofEpochMilli(c.checkIn.reportedForAt).atZone(ZoneId.systemDefault()).toLocalTime()),value)};val byBucket=samples.groupBy({it.first},{it.third});val points=if(grouping==Grouping.RAW)samples.map{it.second to it.third}else byBucket.map{(i,v)->i+.5f to v.average().toFloat()};ChartSeries("${m.displayName} · ${slotName(slot)}",slotColors.getValue(slot),points,byBucket)};TemporalChart(if(m.type==MetricType.BOOLEAN&&grouping!=Grouping.RAW)"${m.displayName} · Доля ответов «Да»" else m.displayName,series,domain,if(m.type==MetricType.SCALE)10f else 1f,legend,m.type==MetricType.BOOLEAN,grouping!=Grouping.RAW,selectedBucket,{selectedBucket=it},cycleDates)}
        val bpSamples=pressure.mapNotNull{p->val z=Instant.ofEpochMilli(p.measuredAt).atZone(ZoneId.systemDefault());if(z.toLocalDate() !in rangeStart..rangeEnd)return@mapNotNull null;val i=TimeDomain.indexOf(domain,z.toLocalDate());if(i<0)null else Triple(i,position(z.toLocalDate(),z.toLocalTime()),p)};fun bpSeries(label:String,color:Color,selector:(BloodPressureMeasurementEntity)->Int?):ChartSeries{val samples=bpSamples.mapNotNull{(i,x,p)->selector(p)?.let{Triple(i,x,it.toFloat())}};val by=samples.groupBy({it.first},{it.third});val points=if(grouping==Grouping.RAW)samples.map{it.second to it.third}else by.map{(i,v)->i+.5f to aggregation.apply(v)};return ChartSeries(label,color,points,by)}
        val pressureSeries=listOf(bpSeries("SYS",Color(0xFFD35F76)){it.systolic},bpSeries("DIA",Color(0xFF4E79A7)){it.diastolic});val pressureRange=PressureChartScale.forVisible(pressureSeries.flatMap{it.points}.map{it.second})
        TemporalChart("Артериальное давление",pressureSeries,domain,pressureRange.max,legend,false,grouping!=Grouping.RAW,selectedBucket,{selectedBucket=it},cycleDates,pressureRange.min)
        TemporalChart("Пульс",listOf(bpSeries("Пульс",Color(0xFF4E795E)){it.pulse}),domain,220f,legend,false,grouping!=Grouping.RAW,selectedBucket,{selectedBucket=it},cycleDates)
    }}
fun slotName(slot:CheckInSlot)=when(slot){CheckInSlot.DAY->"День";CheckInSlot.EVENING->"Вечер";CheckInSlot.EXTRA->"Доп."}
@Composable fun TemporalChart(title:String,series:List<ChartSeries>,domain:List<TimeBucket>,yMax:Float,showLegend:Boolean,boolean:Boolean,aggregated:Boolean,selected:Int,onSelect:(Int)->Unit,cycleDates:List<LocalDate>,yMin:Float=0f){val actual=series.filter{it.points.isNotEmpty()};val chartSurface=MaterialTheme.colorScheme.surfaceVariant;val chartBackground=MaterialTheme.colorScheme.surface;val markerColor=MaterialTheme.colorScheme.primary;val cycleColor=LocalCycleOverlayColor.current;Text(title,style=MaterialTheme.typography.titleMedium);if(showLegend)Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){series.forEach{s->Row(verticalAlignment=Alignment.CenterVertically){Dot(s.color);Text(s.label,style=MaterialTheme.typography.labelSmall)}}};actual.forEach{s->Text("${s.label}: min ${formatValue(s.points.minOf{it.second},boolean,aggregated)}, max ${formatValue(s.points.maxOf{it.second},boolean,aggregated)}",style=MaterialTheme.typography.labelSmall)}
    val count=domain.size.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth()){
        Column(Modifier.width(34.dp).height(180.dp),verticalArrangement=Arrangement.SpaceBetween,horizontalAlignment=Alignment.End){for(i in 5 downTo 0)Text(if(boolean&&!aggregated)when(i){5->"Да";0->"Нет";else->""}else if(boolean)"${i*20}%" else "${(yMin+(yMax-yMin)*i/5).toInt()}",style=MaterialTheme.typography.labelSmall)}
        Canvas(Modifier.weight(1f).height(180.dp).background(chartSurface,RoundedCornerShape(10.dp)).pointerInput(count){detectTapGestures{offset->onSelect(TimeDomain.selectByFraction(domain,offset.x/size.width))}}.padding(8.dp)){
            fun px(v:Float)=v/count*size.width
            for(i in 0..5){val y=size.height*i/5;drawLine(Color.Gray.copy(alpha=.25f),androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width,y))}
            cycleDates.forEach{date->val i=TimeDomain.indexOf(domain,date);if(i>=0){val bucket=domain[i];val bucketDays=maxOf(1L,java.time.temporal.ChronoUnit.DAYS.between(bucket.start,bucket.endExclusive));val fraction=java.time.temporal.ChronoUnit.DAYS.between(bucket.start,date).toFloat()/bucketDays.toFloat();val left=if(bucketDays==1L)px(i.toFloat())else px(i.toFloat()+fraction)-2.dp.toPx();val right=if(bucketDays==1L)px(i+1f)else left+4.dp.toPx();drawRect(cycleColor,topLeft=androidx.compose.ui.geometry.Offset(left,0f),size=androidx.compose.ui.geometry.Size(right-left,size.height))}}
            fun py(value:Float)=size.height-(value-yMin)/(yMax-yMin)*size.height
            actual.forEach{s->val sorted=s.points.sortedBy{it.first};if(!boolean||aggregated)ChartSegments.segments(sorted).forEach{(a,b)->drawLine(s.color,androidx.compose.ui.geometry.Offset(px(a.first),py(a.second)),androidx.compose.ui.geometry.Offset(px(b.first),py(b.second)),3f)};val lo=s.points.minOf{it.second};val hi=s.points.maxOf{it.second};sorted.forEach{p->val extreme=p.second==lo||p.second==hi;drawCircle(if(extreme)chartBackground else s.color,if(extreme)8f else 5f,androidx.compose.ui.geometry.Offset(px(p.first),py(p.second)));if(extreme)drawCircle(s.color,8f,androidx.compose.ui.geometry.Offset(px(p.first),py(p.second)),style=Stroke(3f))}}
            if(selected in domain.indices){val x=px(selected.toFloat()+.5f);drawLine(markerColor,androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Offset(x,size.height),2f)}
        }
    }
    Row(Modifier.fillMaxWidth().padding(start=34.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(domain.first().label,style=MaterialTheme.typography.labelSmall);if(domain.size>2)Text(domain[domain.size/2].label,style=MaterialTheme.typography.labelSmall);Text(domain.last().label,style=MaterialTheme.typography.labelSmall)};if(selected in domain.indices){val bucket=domain[selected];Card{Column(Modifier.padding(10.dp)){Text(bucket.label,fontWeight=FontWeight.SemiBold);val starts=cycleDates.filter{bucket.contains(it)};starts.forEach{Text("Начало цикла: ${pretty(it)}",color=markerColor)};val any=series.any{it.bucketValues[selected].orEmpty().isNotEmpty()};if(!any)Text("Данных нет")else series.forEach{s->val values=s.bucketValues[selected].orEmpty();if(values.isEmpty())Text("${s.label}: Нет данных")else if(aggregated)Text("${s.label}: ${formatValue(s.points.find{it.first.toInt()==selected}?.second?:values.average().toFloat(),boolean,true)} · ${values.size} набл.")else values.forEach{Text("${s.label}: ${formatValue(it,boolean,false)}")}}}}}}
fun formatValue(v:Float,boolean:Boolean,aggregated:Boolean)=if(boolean&&!aggregated)if(v>=.5f)"Да" else "Нет" else if(boolean)"${(v*100).toInt()}%" else if(v%1f==0f)v.toInt().toString() else "%.1f".format(Locale.US,v)

@Composable fun SettingsScreen(vm:MainViewModel){val metrics by vm.metrics.collectAsState();val themeMode by vm.themeMode.collectAsState();val context=LocalContext.current;val scope=rememberCoroutineScope();var exportMessage by remember{mutableStateOf<String?>(null)};var healthMessage by remember{mutableStateOf("")}
    val scheduler=remember{(context.applicationContext as StateTrackerApplication).container.scheduler};var dayReminder by remember{mutableStateOf(scheduler.reminderTime(CheckInSlot.DAY))};var eveningReminder by remember{mutableStateOf(scheduler.reminderTime(CheckInSlot.EVENING))}
    fun chooseReminder(slot:CheckInSlot,current:ReminderTime){TimePickerDialog(context,{_,hour,minute->val updated=ReminderTime(hour,minute);scheduler.updateReminderTime(slot,hour,minute);if(slot==CheckInSlot.DAY)dayReminder=updated else eveningReminder=updated},current.hour,current.minute,true).show()}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->if(uri!=null)scope.launch{runCatching{context.contentResolver.openOutputStream(uri)?.use{CsvExporter((context.applicationContext as StateTrackerApplication).container.dao).write(it)}?:error("Не удалось открыть файл")}.onSuccess{exportMessage="Экспорт готов"}.onFailure{exportMessage="Ошибка экспорта: ${it.message}"}}}
    val health=remember{AndroidHealthConnectDataSource(context)}
    val healthPermission=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()){granted->healthMessage=if(granted.containsAll(health.permissions))"Подключено" else "Нет разрешения"}
    Page("Настройки"){
        Text("Тема",style=MaterialTheme.typography.titleLarge);ThemeMode.entries.forEach{mode->Row(Modifier.fillMaxWidth().clickable{vm.setTheme(mode)},verticalAlignment=Alignment.CenterVertically){RadioButton(themeMode==mode,{vm.setTheme(mode)});Text(when(mode){ThemeMode.SYSTEM->"Как в системе";ThemeMode.LIGHT->"Светлая";ThemeMode.DARK->"Тёмная"})}}
        HorizontalDivider()
        Text("Метрики",style=MaterialTheme.typography.titleLarge);metrics.forEach{m->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Switch(m.enabled,{vm.updateMetric(m.copy(enabled=it))});Spacer(Modifier.width(8.dp));Text(m.displayName,fontWeight=FontWeight.Medium)};Text("Показывать:");Row{LabeledCheck("День",m.showInDay){vm.updateMetric(m.copy(showInDay=it))};LabeledCheck("Вечер",m.showInEvening){vm.updateMetric(m.copy(showInEvening=it))};LabeledCheck("Доп.",m.showInExtra){vm.updateMetric(m.copy(showInExtra=it))}}}}}
        HorizontalDivider();Text("Уведомления",style=MaterialTheme.typography.titleLarge);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton(onClick={chooseReminder(CheckInSlot.DAY,dayReminder)},modifier=Modifier.weight(1f)){Text("День ${dayReminder.formatted()}")};OutlinedButton(onClick={chooseReminder(CheckInSlot.EVENING,eveningReminder)},modifier=Modifier.weight(1f)){Text("Вечер ${eveningReminder.formatted()}")}};Text("Нажми на время, чтобы изменить его. Будущие уведомления перепланируются автоматически.",style=MaterialTheme.typography.bodySmall);val allowed=android.os.Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;Text(if(allowed)"Системное разрешение: выдано" else "Системное разрешение: не выдано");if(!allowed)Button(onClick={notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Разрешить уведомления")}
        HorizontalDivider();Text("Цикл · Health Connect",style=MaterialTheme.typography.titleLarge);val available=health.availability()==HealthConnectClient.SDK_AVAILABLE;Text(if(available)"Доступен${if(healthMessage.isNotBlank())" · $healthMessage" else ""}" else "Недоступен");Button(onClick={if(available)healthPermission.launch(health.permissions)},enabled=available){Text("Подключить")};OutlinedButton(onClick={scope.launch{runCatching{health.read()}.onSuccess{records->val c=(context.applicationContext as StateTrackerApplication).container;records.forEach{c.cycles.import(it.start.atZone(ZoneId.systemDefault()).toLocalDate(),it.id)};healthMessage="Синхронизировано: ${records.size}"}.onFailure{healthMessage="Ошибка: ${it.message}"}}},enabled=available){Text("Синхронизировать")}
        HorizontalDivider();Text("Данные",style=MaterialTheme.typography.titleLarge);Button(onClick={export.launch("state-tracker-export-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.zip")},Modifier.fillMaxWidth()){Text("Экспорт CSV")};exportMessage?.let{Text(it,color=MaterialTheme.colorScheme.primary)};Text("Данные хранятся только на этом устройстве. Удаление приложения удалит локальные данные. Для сохранения истории используй экспорт CSV.",style=MaterialTheme.typography.bodySmall)
    }}
@Composable fun RowScope.LabeledCheck(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically){Checkbox(value,onChange);Text(label,style=MaterialTheme.typography.bodySmall)}}

private val LightStateColors=lightColorScheme(primary=Color(0xFF3F6652),onPrimary=Color.White,background=Color(0xFFFFF2F4),surface=Color(0xFFFFFAFA),surfaceVariant=Color(0xFFF6E4E8),primaryContainer=Color(0xFFD7E7DC))
private val DarkStateColors=darkColorScheme(primary=Color(0xFF9CC9AE),onPrimary=Color(0xFF123728),background=Color(0xFF2B1119),surface=Color(0xFF3A1923),surfaceVariant=Color(0xFF512634),primaryContainer=Color(0xFF315844))
val LocalCycleOverlayColor=staticCompositionLocalOf{Color(0x33A32D4F)}
@Composable fun StateTheme(mode:ThemeMode,content: @Composable () -> Unit){val dark=when(mode){ThemeMode.SYSTEM->isSystemInDarkTheme();ThemeMode.LIGHT->false;ThemeMode.DARK->true};CompositionLocalProvider(LocalCycleOverlayColor provides if(dark)Color(0x55D56A86)else Color(0x33A32D4F)){MaterialTheme(colorScheme=if(dark)DarkStateColors else LightStateColors,content=content)}}

@Composable fun StateApp(deepDate:String?,deepSlot:String?,vm:MainViewModel=viewModel()){
    val nav=rememberNavController();LaunchedEffect(deepDate,deepSlot){if(deepDate!=null&&deepSlot!=null)nav.navigate("checkin/new/$deepDate/$deepSlot")}
    val message by vm.message.collectAsState();if(message!=null)AlertDialog(onDismissRequest=vm::clearMessage,confirmButton={TextButton(onClick=vm::clearMessage){Text("Понятно")}},text={Text(message!!)})
    val roots=listOf("today" to "Сегодня","calendar" to "Календарь","analytics" to "Графики","settings" to "Настройки")
    Scaffold(bottomBar={NavigationBar{val route=nav.currentBackStackEntryAsState().value?.destination?.route;roots.forEachIndexed{i,(r,t)->NavigationBarItem(selected=route==r,onClick={nav.navigate(r){popUpTo("today");launchSingleTop=true}},icon={Icon(listOf(Icons.Default.Today,Icons.Default.CalendarMonth,Icons.Default.ShowChart,Icons.Default.Settings)[i],t)},label={Text(t)})}}}){pad->
        NavHost(nav,"today",Modifier.padding(pad)){
            composable("today"){TodayScreen(nav,vm)}
            composable("calendar"){CalendarScreen(nav,vm)}
            composable("analytics"){AnalyticsScreen(vm)}
            composable("settings"){SettingsScreen(vm)}
            composable("day/{date}",arguments=listOf(navArgument("date"){type=NavType.StringType})){DayScreen(nav,vm,LocalDate.parse(it.arguments!!.getString("date")))}
            composable("checkin/{id}"){CheckInScreen(nav,vm,it.arguments!!.getString("id"),null,null)}
            composable("checkin/new/{date}/{slot}"){CheckInScreen(nav,vm,null,LocalDate.parse(it.arguments!!.getString("date")),CheckInSlot.valueOf(it.arguments!!.getString("slot")!!))}
            composable("pressure?checkin={checkin}&measurement={measurement}",arguments=listOf(navArgument("checkin"){nullable=true;defaultValue=null},navArgument("measurement"){nullable=true;defaultValue=null})){PressureScreen(nav,vm,it.arguments?.getString("checkin"),it.arguments?.getString("measurement"))}
        }
    }
}

@Composable fun Page(title:String,content: @Composable ColumnScope.() -> Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold);content();Spacer(Modifier.height(80.dp))}}
fun pretty(date:LocalDate)=date.format(DateTimeFormatter.ofPattern("d MMMM",Locale("ru")))

@Composable fun TodayScreen(nav:NavController,vm:MainViewModel){val all by vm.all.collectAsState();val pressure by vm.pressure.collectAsState();val today=LocalDate.now();val entries=all.filter{it.checkIn.targetDate==today.toString()};Page("Сегодня · ${pretty(today)}"){
    listOf(CheckInSlot.DAY to "День",CheckInSlot.EVENING to "Вечер").forEach{(slot,label)->val found=entries.find{it.checkIn.slot==slot};Card(onClick={if(found==null)nav.navigate("checkin/new/$today/$slot")else nav.navigate("checkin/${found.checkIn.id}")},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp)){Text(label,style=MaterialTheme.typography.titleLarge);Text(if(found==null)"Не заполнено" else "Заполнено · ${Instant.ofEpochMilli(found.checkIn.createdAt).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}",color=if(found==null)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary);val summary=found?.observations?.take(2)?.joinToString(" · "){o->vm.metrics.value.find{it.id==o.metricId}?.displayName+" "+(o.numericValue?.toInt()?:if(o.booleanValue==true)"Да" else "Нет")};if(!summary.isNullOrBlank())Text(summary)}}}
    Button(onClick={nav.navigate("checkin/new/$today/${CheckInSlot.EXTRA}")},Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text(" Дополнительное состояние")}
    OutlinedButton(onClick={nav.navigate("pressure")},Modifier.fillMaxWidth()){Text("+ Давление")}
    pressure.filter{Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDate()==today}.takeLast(3).forEach{Text("${it.systolic} / ${it.diastolic}"+(it.pulse?.let{" · пульс $it"}?:""))}
    MascotGarden()
}}

@Composable fun CheckInScreen(nav:NavController,vm:MainViewModel,id:String?,newDate:LocalDate?,newSlot:CheckInSlot?){var loaded by remember{mutableStateOf(id==null)};var actualId by remember{mutableStateOf(id)};var date by remember{mutableStateOf(newDate?:LocalDate.now())};var slot by remember{mutableStateOf(newSlot?:CheckInSlot.EXTRA)};var comment by remember{mutableStateOf("")};val answers=remember{mutableStateMapOf<String,Answer>()};var dirty by remember{mutableStateOf(false)};var confirmExit by remember{mutableStateOf(false)};var confirmDelete by remember{mutableStateOf(false)};var anchorMetric by remember{mutableStateOf<MetricDefinitionEntity?>(null)};val metrics by vm.metrics.collectAsState();val allPressure by vm.pressure.collectAsState()
    LaunchedEffect(id,newDate,newSlot){val found=if(id!=null)vm.find(id) else if(newSlot!=CheckInSlot.EXTRA&&newDate!=null)vm.regular(newDate,newSlot!!) else null;if(found!=null){actualId=found.checkIn.id;date=LocalDate.parse(found.checkIn.targetDate);slot=found.checkIn.slot;comment=found.checkIn.comment.orEmpty();found.observations.forEach{answers[it.metricId]=Answer(it.numericValue,it.booleanValue)}};loaded=true}
    if(!loaded){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return}
    val visible=metrics.filter{it.enabled&&when(slot){CheckInSlot.DAY->it.showInDay;CheckInSlot.EVENING->it.showInEvening;CheckInSlot.EXTRA->it.showInExtra}}
    if(confirmExit)AlertDialog(onDismissRequest={confirmExit=false},confirmButton={TextButton(onClick={nav.popBackStack()}){Text("Выйти")}},dismissButton={TextButton(onClick={confirmExit=false}){Text("Остаться")}},text={Text("Изменения не сохранены. Выйти?")})
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},confirmButton={TextButton(onClick={vm.deleteCheckIn(actualId!!){nav.popBackStack()}}){Text("Удалить")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Отмена")}},text={Text("Удалить это состояние? Давление останется в истории.")})
    anchorMetric?.let{m->AlertDialog(onDismissRequest={anchorMetric=null},confirmButton={TextButton(onClick={anchorMetric=null}){Text("Закрыть")}},title={Text(m.displayName)},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){m.anchorLow?.let{Text("0\n$it")};m.anchorMid?.let{Text("5\n$it")};m.anchorHigh?.let{Text("10\n$it")}}})}
    Page("${when(slot){CheckInSlot.DAY->"День";CheckInSlot.EVENING->"Вечер";CheckInSlot.EXTRA->"Дополнительное состояние"}} — ${pretty(date)}"){
        if(LocalDate.now()>date)AssistChip(onClick={},label={Text("Добавлено задним числом")})
        visible.forEach{m->if(m.type==MetricType.SCALE){val current=answers[m.id]?.numeric?.toFloat();Row(Modifier.fillMaxWidth().then(if(m.anchorLow!=null)Modifier.clickable{anchorMetric=m}.semantics{contentDescription="${m.displayName}. Показать описание шкалы."}else Modifier),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(m.displayName+(if(m.anchorLow!=null)"  ⓘ" else ""),style=MaterialTheme.typography.titleMedium);Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer){Text(current?.toInt()?.toString()?:"—",Modifier.padding(horizontal=12.dp,vertical=4.dp),fontWeight=FontWeight.Bold)}};Slider(value=current?:0f,onValueChange={answers[m.id]=Answer(numeric=it.toInt().toDouble());dirty=true},valueRange=0f..10f,steps=9);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("0",style=MaterialTheme.typography.labelSmall);if(current!=null)Text("Очистить",Modifier.clickable{answers.remove(m.id);dirty=true},color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium);Text("10",style=MaterialTheme.typography.labelSmall)}}else{Text(m.displayName,style=MaterialTheme.typography.titleMedium);SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf(null,false,true).forEachIndexed{i,v->SegmentedButton(selected=answers[m.id]?.boolean==v&&answers.containsKey(m.id),onClick={if(v==null)answers.remove(m.id)else answers[m.id]=Answer(boolean=v);dirty=true},shape=SegmentedButtonDefaults.itemShape(i,3)){Text(listOf("Не выбрано","Нет","Да")[i])}}}}}
        OutlinedTextField(comment,{comment=it;dirty=true},label={Text("Комментарий")},minLines=3,modifier=Modifier.fillMaxWidth())
        Text("Артериальное давление",style=MaterialTheme.typography.titleMedium)
        val linked=allPressure.filter{it.checkinId==actualId};linked.forEach{p->OutlinedCard(onClick={nav.navigate("pressure?checkin=$actualId&measurement=${p.id}")},Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${p.systolic} / ${p.diastolic}",fontWeight=FontWeight.SemiBold);p.pulse?.let{Text("Пульс $it")};Text("Изменить",color=MaterialTheme.colorScheme.primary)}}}
        OutlinedButton(onClick={if(actualId==null)vm.saveCheckIn(null,date,slot,null,answers,comment){newId->actualId=newId;dirty=false;nav.navigate("pressure?checkin=$newId")}else nav.navigate("pressure?checkin=$actualId")},Modifier.fillMaxWidth()){Text(if(linked.isEmpty())"+ Добавить давление" else "+ Ещё измерение")}
        Button(onClick={vm.saveCheckIn(actualId,date,slot,null,answers,comment){dirty=false;nav.popBackStack()}},Modifier.fillMaxWidth()){Text("Сохранить")}
        if(actualId!=null)TextButton(onClick={confirmDelete=true},Modifier.fillMaxWidth()){Text("Удалить")}
        TextButton(onClick={if(dirty)confirmExit=true else nav.popBackStack()},Modifier.fillMaxWidth()){Text("Назад")}
    }
}
