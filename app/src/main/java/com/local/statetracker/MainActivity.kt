package com.local.statetracker

import android.Manifest
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
import com.local.statetracker.ocr.BloodPressureParser
import com.local.statetracker.settings.ThemeMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri:Uri?->if(uri!=null){loading=true;TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(InputImage.fromFilePath(context,uri)).addOnSuccessListener{text->val p=BloodPressureParser().parse(text.text);sys=p.systolic?.toString().orEmpty();dia=p.diastolic?.toString().orEmpty();pulse=p.pulse?.toString().orEmpty();ocrMessage=if(p.systolic==null)"Не удалось уверенно распознать значения. Введи их вручную." else null;source=PressureSource.OCR;loading=false}.addOnFailureListener{ocrMessage="Не удалось распознать изображение. Введи значения вручную.";loading=false}}}
    Page(if(measurementId==null)"Давление" else "Изменить давление"){
        if(loading)CircularProgressIndicator();OutlinedButton(onClick={picker.launch("image/*")},Modifier.fillMaxWidth()){Text("Выбрать фото для распознавания")}
        OutlinedTextField(sys,{sys=it},label={Text("Верхнее / SYS")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(dia,{dia=it},label={Text("Нижнее / DIA")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(pulse,{pulse=it},label={Text("Пульс")},modifier=Modifier.fillMaxWidth())
        ocrMessage?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={vm.savePressure(measurementId,sys.toIntOrNull(),dia.toIntOrNull(),pulse.toIntOrNull(),source,checkinId){nav.popBackStack()}},Modifier.fillMaxWidth()){Text("Сохранить")}
    }}

data class ChartSeries(val label:String,val color:Color,val points:List<Pair<Float,Float>>)
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AnalyticsScreen(vm:MainViewModel){val all by vm.all.collectAsState();val pressure by vm.pressure.collectAsState();val metrics by vm.metrics.collectAsState();var days by remember{mutableIntStateOf(30)};var selected by remember{mutableStateOf(setOf("energy","anxiety"))};var slots by remember{mutableStateOf(setOf(CheckInSlot.DAY,CheckInSlot.EVENING))};var grouping by remember{mutableStateOf(Grouping.RAW)};var aggregation by remember{mutableStateOf(PressureAggregation.MEAN)};var picker by remember{mutableStateOf(false)};var advanced by remember{mutableStateOf(false)};var legend by remember{mutableStateOf(true)}
    if(picker)ModalBottomSheet(onDismissRequest={picker=false}){Column(Modifier.padding(20.dp).padding(bottom=40.dp)){Text("Метрики",style=MaterialTheme.typography.headlineSmall);metrics.forEach{m->Row(Modifier.fillMaxWidth().clickable{selected=if(m.id in selected)selected-m.id else selected+m.id},verticalAlignment=Alignment.CenterVertically){Checkbox(m.id in selected,null);Text(m.displayName)}}}}
    Page("Графики"){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){metrics.filter{it.id in selected}.take(2).forEach{AssistChip({},label={Text(it.displayName)})};if(selected.size>2)AssistChip({},label={Text("+${selected.size-2}")});TextButton(onClick={picker=true}){Text("Изменить")}}
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(7,30,90).forEach{d->FilterChip(days==d,{days=d},{Text(if(d==90)"3 мес." else "$d дней")})};TextButton(onClick={advanced=!advanced}){Text("Настройки")}}
        if(advanced)Card{Column(Modifier.padding(12.dp)){Text("Группировка");Row{Grouping.entries.forEach{g->FilterChip(grouping==g,{grouping=g},{Text(when(g){Grouping.RAW->"Raw";Grouping.DAY->"Дни";Grouping.WEEK->"Недели";Grouping.MONTH->"Месяцы"})})}};Text("Слоты");Row{CheckInSlot.entries.forEach{s->FilterChip(s in slots,{slots=if(s in slots)slots-s else slots+s},{Text(when(s){CheckInSlot.DAY->"День";CheckInSlot.EVENING->"Вечер";CheckInSlot.EXTRA->"Доп."})})}};Row(verticalAlignment=Alignment.CenterVertically){Switch(legend,{legend=it});Text("Показывать легенду")};if(grouping!=Grouping.RAW){Text("Агрегация давления");Row{PressureAggregation.entries.forEach{a->FilterChip(aggregation==a,{aggregation=a},{Text(a.ru)})}}}}}
        val cutoff=LocalDate.now().minusDays(days.toLong()-1);val slotColors=mapOf(CheckInSlot.DAY to Color(0xFF4E795E),CheckInSlot.EVENING to Color(0xFFD86B83),CheckInSlot.EXTRA to Color(0xFF8064A2))
        metrics.filter{it.id in selected}.forEach{m->val series=slots.mapNotNull{slot->val raw=all.filter{it.checkIn.slot==slot&&LocalDate.parse(it.checkIn.targetDate)>=cutoff}.mapNotNull{c->c.observations.find{it.metricId==m.id}?.let{o->val v=o.numericValue?:o.booleanValue?.let{b->if(b)1.0 else 0.0};v?.let{val z=Instant.ofEpochMilli(c.checkIn.reportedForAt).atZone(ZoneId.systemDefault());(LocalDate.parse(c.checkIn.targetDate).toEpochDay()-cutoff.toEpochDay()+z.toLocalTime().toSecondOfDay()/86400.0).toFloat() to it.toFloat()}}};val points=if(grouping==Grouping.RAW)raw else raw.groupBy{when(grouping){Grouping.DAY->it.first.toInt();Grouping.WEEK->it.first.toInt()/7;Grouping.MONTH->it.first.toInt()/30;else->it.first.toInt()}}.map{(k,v)->k.toFloat()*when(grouping){Grouping.WEEK->7;Grouping.MONTH->30;else->1} to v.map{it.second}.average().toFloat()};if(points.isEmpty())null else ChartSeries("${m.displayName} · ${slotName(slot)}",slotColors.getValue(slot),points)};InteractiveChart(if(m.type==MetricType.BOOLEAN&&grouping!=Grouping.RAW)"${m.displayName} · Доля ответов «Да»" else m.displayName,series,days,if(m.type==MetricType.SCALE)10f else 1f,legend,m.type==MetricType.BOOLEAN,grouping!=Grouping.RAW)}
        val bp=pressure.filter{Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDate()>=cutoff};fun bpPoints(selector:(BloodPressureMeasurementEntity)->Int?):List<Pair<Float,Float>>{val raw=bp.mapNotNull{p->selector(p)?.let{val z=Instant.ofEpochMilli(p.measuredAt).atZone(ZoneId.systemDefault());(z.toLocalDate().toEpochDay()-cutoff.toEpochDay()+z.toLocalTime().toSecondOfDay()/86400.0).toFloat() to it.toFloat()}};if(grouping==Grouping.RAW)return raw;return raw.groupBy{when(grouping){Grouping.DAY->it.first.toInt();Grouping.WEEK->it.first.toInt()/7;Grouping.MONTH->it.first.toInt()/30;else->it.first.toInt()}}.map{(k,v)->k.toFloat()*when(grouping){Grouping.WEEK->7;Grouping.MONTH->30;else->1} to aggregation.apply(v.map{it.second})}}
        InteractiveChart("Артериальное давление",listOf(ChartSeries("SYS",Color(0xFFD35F76),bpPoints{it.systolic}),ChartSeries("DIA",Color(0xFF4E79A7),bpPoints{it.diastolic})),days,250f,legend)
        InteractiveChart("Пульс",listOf(ChartSeries("Пульс",Color(0xFF4E795E),bpPoints{it.pulse})),days,220f,legend)
    }}
fun slotName(slot:CheckInSlot)=when(slot){CheckInSlot.DAY->"День";CheckInSlot.EVENING->"Вечер";CheckInSlot.EXTRA->"Доп."}
@Composable fun InteractiveChart(title:String,series:List<ChartSeries>,days:Int,yMax:Float,showLegend:Boolean,boolean:Boolean=false,aggregated:Boolean=false){var selectedX by remember{mutableStateOf<Float?>(null)};val actual=series.filter{it.points.isNotEmpty()};val chartSurface=MaterialTheme.colorScheme.surfaceVariant;val chartBackground=MaterialTheme.colorScheme.surface;val markerColor=MaterialTheme.colorScheme.primary;Text(title,style=MaterialTheme.typography.titleMedium);if(actual.isEmpty()){Text("За выбранный период данных нет.",Modifier.fillMaxWidth().padding(24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant);return};if(showLegend)Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){actual.forEach{s->Row(verticalAlignment=Alignment.CenterVertically){Dot(s.color);Text(s.label,style=MaterialTheme.typography.labelSmall)}}};actual.forEach{s->Text("${s.label}: min ${formatValue(s.points.minOf{it.second},boolean,aggregated)}, max ${formatValue(s.points.maxOf{it.second},boolean,aggregated)}",style=MaterialTheme.typography.labelSmall)}
    Row(Modifier.fillMaxWidth()){Column(Modifier.width(34.dp).height(180.dp),verticalArrangement=Arrangement.SpaceBetween,horizontalAlignment=Alignment.End){for(i in 5 downTo 0)Text(if(boolean&&!aggregated)when(i){5->"Да";0->"Нет";else->""}else if(boolean)"${i*20}%" else "${(yMax*i/5).toInt()}",style=MaterialTheme.typography.labelSmall)};Canvas(Modifier.weight(1f).height(180.dp).background(chartSurface,RoundedCornerShape(10.dp)).pointerInput(days){detectTapGestures{offset->selectedX=(offset.x/size.width*(days-1)).coerceIn(0f,(days-1).toFloat())}}.padding(8.dp)){for(i in 0..5){val y=size.height*i/5;drawLine(Color.Gray.copy(alpha=.25f),androidx.compose.ui.geometry.Offset(0f,y),androidx.compose.ui.geometry.Offset(size.width,y))};actual.forEach{s->val sorted=s.points.sortedBy{it.first};val path=Path();sorted.forEachIndexed{i,p->val x=p.first/(days-1).coerceAtLeast(1)*size.width;val y=size.height-p.second/yMax*size.height;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};if(!boolean||aggregated)drawPath(path,s.color,style=Stroke(3f));val lo=s.points.minOf{it.second};val hi=s.points.maxOf{it.second};sorted.forEach{p->val extreme=p.second==lo||p.second==hi;drawCircle(if(extreme)chartBackground else s.color,if(extreme)8f else 5f,androidx.compose.ui.geometry.Offset(p.first/(days-1).coerceAtLeast(1)*size.width,size.height-p.second/yMax*size.height));if(extreme)drawCircle(s.color,8f,androidx.compose.ui.geometry.Offset(p.first/(days-1).coerceAtLeast(1)*size.width,size.height-p.second/yMax*size.height),style=Stroke(3f))}};selectedX?.let{x->drawLine(markerColor,androidx.compose.ui.geometry.Offset(x/(days-1)*size.width,0f),androidx.compose.ui.geometry.Offset(x/(days-1)*size.width,size.height),strokeWidth=2f)}}}
    Row(Modifier.fillMaxWidth().padding(start=34.dp),horizontalArrangement=Arrangement.SpaceBetween){val start=LocalDate.now().minusDays(days.toLong()-1);Text(axisDate(start,days),style=MaterialTheme.typography.labelSmall);Text(axisDate(start.plusDays((days/2).toLong()),days),style=MaterialTheme.typography.labelSmall);Text(axisDate(LocalDate.now(),days),style=MaterialTheme.typography.labelSmall)};selectedX?.let{x->val nearest=actual.flatMap{it.points}.minByOrNull{kotlin.math.abs(it.first-x)};val exact=nearest?.first?:x;val day=exact.toInt();val minutes=((exact-day)*1440).toInt();Card{Column(Modifier.padding(10.dp)){Text(pretty(LocalDate.now().minusDays(days.toLong()-1).plusDays(day.toLong()))+(if(minutes>0)" · ${LocalTime.of(minutes/60,minutes%60).format(DateTimeFormatter.ofPattern("HH:mm"))}" else ""),fontWeight=FontWeight.SemiBold);actual.forEach{s->s.points.filter{kotlin.math.abs(it.first-exact)<.02f}.forEach{Text("${s.label}: ${formatValue(it.second,boolean,aggregated)}")}}}}}}
fun axisDate(date:LocalDate,days:Int)=date.format(DateTimeFormatter.ofPattern(if(days<=7)"EEE, d" else "d MMM",Locale.forLanguageTag("ru")))
fun formatValue(v:Float,boolean:Boolean,aggregated:Boolean)=if(boolean&&!aggregated)if(v>=.5f)"Да" else "Нет" else if(boolean)"${(v*100).toInt()}%" else if(v%1f==0f)v.toInt().toString() else "%.1f".format(Locale.US,v)

@Composable fun SettingsScreen(vm:MainViewModel){val metrics by vm.metrics.collectAsState();val themeMode by vm.themeMode.collectAsState();val context=LocalContext.current;val scope=rememberCoroutineScope();var exportMessage by remember{mutableStateOf<String?>(null)};var healthMessage by remember{mutableStateOf("")}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")){uri->if(uri!=null)scope.launch{runCatching{context.contentResolver.openOutputStream(uri)?.use{CsvExporter((context.applicationContext as StateTrackerApplication).container.dao).write(it)}?:error("Не удалось открыть файл")}.onSuccess{exportMessage="Экспорт готов"}.onFailure{exportMessage="Ошибка экспорта: ${it.message}"}}}
    val health=remember{AndroidHealthConnectDataSource(context)}
    val healthPermission=rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()){granted->healthMessage=if(granted.containsAll(health.permissions))"Подключено" else "Нет разрешения"}
    Page("Настройки"){
        Text("Тема",style=MaterialTheme.typography.titleLarge);ThemeMode.entries.forEach{mode->Row(Modifier.fillMaxWidth().clickable{vm.setTheme(mode)},verticalAlignment=Alignment.CenterVertically){RadioButton(themeMode==mode,{vm.setTheme(mode)});Text(when(mode){ThemeMode.SYSTEM->"Как в системе";ThemeMode.LIGHT->"Светлая";ThemeMode.DARK->"Тёмная"})}}
        HorizontalDivider()
        Text("Метрики",style=MaterialTheme.typography.titleLarge);metrics.forEach{m->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Switch(m.enabled,{vm.updateMetric(m.copy(enabled=it))});Spacer(Modifier.width(8.dp));Text(m.displayName,fontWeight=FontWeight.Medium)};Text("Показывать:");Row{LabeledCheck("День",m.showInDay){vm.updateMetric(m.copy(showInDay=it))};LabeledCheck("Вечер",m.showInEvening){vm.updateMetric(m.copy(showInEvening=it))};LabeledCheck("Доп.",m.showInExtra){vm.updateMetric(m.copy(showInExtra=it))}}}}}
        HorizontalDivider();Text("Уведомления",style=MaterialTheme.typography.titleLarge);Text("День: около 13:00\nВечер: около 19:00");val allowed=android.os.Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;Text(if(allowed)"Системное разрешение: выдано" else "Системное разрешение: не выдано");if(!allowed)Button(onClick={notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}){Text("Разрешить уведомления")}
        HorizontalDivider();Text("Цикл · Health Connect",style=MaterialTheme.typography.titleLarge);val available=health.availability()==HealthConnectClient.SDK_AVAILABLE;Text(if(available)"Доступен${if(healthMessage.isNotBlank())" · $healthMessage" else ""}" else "Недоступен");Button(onClick={if(available)healthPermission.launch(health.permissions)},enabled=available){Text("Подключить")};OutlinedButton(onClick={scope.launch{runCatching{health.read()}.onSuccess{records->val c=(context.applicationContext as StateTrackerApplication).container;records.forEach{c.cycles.import(it.start.atZone(ZoneId.systemDefault()).toLocalDate(),it.id)};healthMessage="Синхронизировано: ${records.size}"}.onFailure{healthMessage="Ошибка: ${it.message}"}}},enabled=available){Text("Синхронизировать")}
        HorizontalDivider();Text("Данные",style=MaterialTheme.typography.titleLarge);Button(onClick={export.launch("state-tracker-export-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.zip")},Modifier.fillMaxWidth()){Text("Экспорт CSV")};exportMessage?.let{Text(it,color=MaterialTheme.colorScheme.primary)};Text("Данные хранятся только на этом устройстве. Удаление приложения удалит локальные данные. Для сохранения истории используй экспорт CSV.",style=MaterialTheme.typography.bodySmall)
    }}
@Composable fun RowScope.LabeledCheck(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically){Checkbox(value,onChange);Text(label,style=MaterialTheme.typography.bodySmall)}}

private val LightStateColors=lightColorScheme(primary=Color(0xFF3F6652),onPrimary=Color.White,background=Color(0xFFFFF2F4),surface=Color(0xFFFFFAFA),surfaceVariant=Color(0xFFF6E4E8),primaryContainer=Color(0xFFD7E7DC))
private val DarkStateColors=darkColorScheme(primary=Color(0xFF9CC9AE),onPrimary=Color(0xFF123728),background=Color(0xFF2B1119),surface=Color(0xFF3A1923),surfaceVariant=Color(0xFF512634),primaryContainer=Color(0xFF315844))
@Composable fun StateTheme(mode:ThemeMode,content: @Composable () -> Unit){val dark=when(mode){ThemeMode.SYSTEM->isSystemInDarkTheme();ThemeMode.LIGHT->false;ThemeMode.DARK->true};MaterialTheme(colorScheme=if(dark)DarkStateColors else LightStateColors,content=content)}

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
