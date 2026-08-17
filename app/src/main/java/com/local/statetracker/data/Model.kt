package com.local.statetracker.data

import androidx.room.*

enum class CheckInSlot { DAY, EVENING, EXTRA }
enum class MetricType { SCALE, BOOLEAN }
enum class PressureSource { MANUAL, OCR }
enum class CycleSource { MANUAL, HEALTH_CONNECT }

@Entity(tableName = "metrics")
data class MetricDefinitionEntity(
    @PrimaryKey val id: String, val displayName: String, val type: MetricType,
    val minValue: Int? = null, val maxValue: Int? = null, val enabled: Boolean = true,
    val showInDay: Boolean = true, val showInEvening: Boolean = true, val showInExtra: Boolean = true,
    val sortOrder: Int, val anchorLow: String? = null, val anchorMid: String? = null, val anchorHigh: String? = null
)

@Entity(tableName = "checkins", indices = [Index("targetDate"), Index(value=["targetDate","regularSlotKey"], unique=true)])
data class CheckInEntity(
    @PrimaryKey val id: String, val targetDate: String, val slot: CheckInSlot,
    val regularSlotKey: String?, val reportedForAt: Long, val createdAt: Long,
    val updatedAt: Long? = null, val isBackfilled: Boolean, val comment: String? = null
)

@Entity(tableName = "observations", primaryKeys = ["checkinId", "metricId"], foreignKeys = [
    ForeignKey(entity=CheckInEntity::class,parentColumns=["id"],childColumns=["checkinId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=MetricDefinitionEntity::class,parentColumns=["id"],childColumns=["metricId"],onDelete=ForeignKey.NO_ACTION)
], indices=[Index("checkinId"),Index("metricId")])
data class ObservationEntity(val checkinId:String,val metricId:String,val numericValue:Double?=null,val booleanValue:Boolean?=null)

@Entity(tableName="blood_pressure", foreignKeys=[ForeignKey(entity=CheckInEntity::class,parentColumns=["id"],childColumns=["checkinId"],onDelete=ForeignKey.SET_NULL)], indices=[Index("checkinId"),Index("measuredAt")])
data class BloodPressureMeasurementEntity(@PrimaryKey val id:String,val measuredAt:Long,val createdAt:Long,val updatedAt:Long?=null,val systolic:Int,val diastolic:Int,val pulse:Int?=null,val source:PressureSource,val checkinId:String?=null)

@Entity(tableName="cycle_events",indices=[Index(value=["date"],unique=true)])
data class CycleEventEntity(@PrimaryKey val id:String,val date:String,val source:CycleSource,val externalRecordId:String?=null,val createdAt:Long)

data class CheckInWithObservations(@Embedded val checkIn:CheckInEntity,@Relation(parentColumn="id",entityColumn="checkinId") val observations:List<ObservationEntity>)

object Converters {
    @TypeConverter fun slot(v:String)=CheckInSlot.valueOf(v)
    @TypeConverter fun slot(v:CheckInSlot)=v.name
    @TypeConverter fun metric(v:String)=MetricType.valueOf(v)
    @TypeConverter fun metric(v:MetricType)=v.name
    @TypeConverter fun pressure(v:String)=PressureSource.valueOf(v)
    @TypeConverter fun pressure(v:PressureSource)=v.name
    @TypeConverter fun cycle(v:String)=CycleSource.valueOf(v)
    @TypeConverter fun cycle(v:CycleSource)=v.name
}
