package com.local.statetracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.local.statetracker.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*

class MainViewModel(app:Application):AndroidViewModel(app){
    private val c=(app as StateTrackerApplication).container
    val metrics=c.dao.observeMetrics().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val all=c.checkIns.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val pressure=c.pressure.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    val cycles=c.cycles.observeAll().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _message=MutableStateFlow<String?>(null)
    val message:StateFlow<String?> = _message.asStateFlow()
    suspend fun find(id:String)=c.checkIns.find(id)
    suspend fun regular(date:LocalDate,slot:CheckInSlot)=c.checkIns.findRegular(date,slot)
    fun saveCheckIn(id:String?,date:LocalDate,slot:CheckInSlot,time:LocalTime?,answers:Map<String,Answer>,comment:String,onDone:(String)->Unit)=viewModelScope.launch{runCatching{c.checkIns.save(id,date,slot,time,answers,comment)}.onSuccess{if(slot!=CheckInSlot.EXTRA)c.scheduler.cancelNotification(date,slot);onDone(it)}.onFailure{_message.value=it.message}}
    fun deleteCheckIn(id:String,onDone:()->Unit)=viewModelScope.launch{runCatching{c.checkIns.delete(id)}.onSuccess{onDone()}.onFailure{_message.value=it.message}}
    fun savePressure(sys:Int?,dia:Int?,pulse:Int?,source:PressureSource,checkinId:String?,onDone:()->Unit)=viewModelScope.launch{runCatching{c.pressure.save(sys,dia,pulse,Instant.now(),source,checkinId)}.onSuccess{onDone()}.onFailure{_message.value=it.message}}
    fun toggleCycle(date:LocalDate,exists:Boolean)=viewModelScope.launch{c.cycles.toggle(date,exists)}
    fun updateMetric(m:MetricDefinitionEntity)=viewModelScope.launch{c.dao.updateMetric(m)}
    fun clearMessage(){_message.value=null}
}
