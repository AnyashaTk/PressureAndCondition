package com.local.statetracker.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.time.*
import java.util.UUID

data class Answer(val numeric:Double?=null,val boolean:Boolean?=null)

class CheckInRepository(private val db:AppDatabase, private val clock:Clock=Clock.systemDefaultZone()) {
    private val dao=db.dao()
    fun observeDate(date:LocalDate)=dao.observeDate(date.toString())
    fun observeAll()=dao.observeAll()
    suspend fun find(id:String)=dao.checkIn(id)
    suspend fun findRegular(date:LocalDate,slot:CheckInSlot)=dao.regular(date.toString(),slot)
    suspend fun exists(date:LocalDate,slot:CheckInSlot)=dao.exists(date.toString(),slot)
    suspend fun save(id:String?,date:LocalDate,slot:CheckInSlot,extraTime:LocalTime?,answers:Map<String,Answer>,comment:String?):String {
        require(answers.values.any { it.numeric!=null || it.boolean!=null } || !comment.isNullOrBlank()) { "Нужно заполнить хотя бы одно поле или комментарий" }
        answers.values.forEach { require(it.numeric==null || it.numeric in 0.0..10.0); require(it.numeric==null || it.boolean==null) }
        val now=clock.instant(); val existing=id?.let{dao.checkIn(it)?.checkIn}
        val zone=clock.zone
        val reported=date.atTime(when(slot){CheckInSlot.DAY->LocalTime.of(13,0);CheckInSlot.EVENING->LocalTime.of(19,0);CheckInSlot.EXTRA->extraTime?:LocalTime.now(clock)}).atZone(zone).toInstant().toEpochMilli()
        return db.withTransaction {
            val actual=existing ?: if(slot!=CheckInSlot.EXTRA) dao.regular(date.toString(),slot)?.checkIn else null
            val entity=actual?.copy(reportedForAt=reported,updatedAt=now.toEpochMilli(),comment=comment?.trim()?.ifBlank{null})
                ?: CheckInEntity(UUID.randomUUID().toString(),date.toString(),slot,if(slot==CheckInSlot.EXTRA)null else slot.name,reported,now.toEpochMilli(),isBackfilled=LocalDate.now(clock)>date,comment=comment?.trim()?.ifBlank{null})
            if(actual==null) dao.insertCheckIn(entity) else dao.updateCheckIn(entity)
            dao.clearObservations(entity.id)
            dao.putObservations(answers.mapNotNull { (metric,value) -> if(value.numeric==null&&value.boolean==null)null else ObservationEntity(entity.id,metric,value.numeric,value.boolean) })
            entity.id
        }
    }
    suspend fun delete(id:String)=dao.deleteCheckIn(id)
}

class PressureRepository(private val dao:AppDao,private val clock:Clock=Clock.systemDefaultZone()){
    fun observeAll()=dao.observePressure()
    suspend fun save(systolic:Int?,diastolic:Int?,pulse:Int?,measuredAt:Instant,source:PressureSource,checkinId:String?=null){
        require(systolic!=null&&systolic>0&&diastolic!=null&&diastolic>0){"Заполните SYS и DIA положительными числами"}
        require(pulse==null||pulse>0){"Пульс должен быть положительным числом"}
        dao.insertPressure(BloodPressureMeasurementEntity(UUID.randomUUID().toString(),measuredAt.toEpochMilli(),clock.millis(),systolic=systolic,diastolic=diastolic,pulse=pulse,source=source,checkinId=checkinId))
    }
    suspend fun delete(id:String)=dao.deletePressure(id)
}

class CycleRepository(private val dao:AppDao,private val clock:Clock=Clock.systemDefaultZone()){
    fun observeAll()=dao.observeCycles()
    suspend fun toggle(date:LocalDate,exists:Boolean){if(exists)dao.deleteCycle(date.toString()) else dao.insertCycle(CycleEventEntity(UUID.randomUUID().toString(),date.toString(),CycleSource.MANUAL,createdAt=clock.millis()))}
    suspend fun import(date:LocalDate,externalId:String)=dao.insertCycle(CycleEventEntity(UUID.randomUUID().toString(),date.toString(),CycleSource.HEALTH_CONNECT,externalId,clock.millis()))
}
