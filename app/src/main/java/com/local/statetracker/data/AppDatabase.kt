package com.local.statetracker.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface AppDao {
    @Query("SELECT * FROM metrics ORDER BY sortOrder") fun observeMetrics():Flow<List<MetricDefinitionEntity>>
    @Query("SELECT * FROM metrics ORDER BY sortOrder") suspend fun metrics():List<MetricDefinitionEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putMetrics(items:List<MetricDefinitionEntity>)
    @Update suspend fun updateMetric(item:MetricDefinitionEntity)
    @Transaction @Query("SELECT * FROM checkins WHERE targetDate=:date ORDER BY reportedForAt") fun observeDate(date:String):Flow<List<CheckInWithObservations>>
    @Transaction @Query("SELECT * FROM checkins ORDER BY reportedForAt") fun observeAll():Flow<List<CheckInWithObservations>>
    @Transaction @Query("SELECT * FROM checkins ORDER BY reportedForAt") suspend fun allCheckIns():List<CheckInWithObservations>
    @Transaction @Query("SELECT * FROM checkins WHERE id=:id") suspend fun checkIn(id:String):CheckInWithObservations?
    @Transaction @Query("SELECT * FROM checkins WHERE targetDate=:date AND slot=:slot LIMIT 1") suspend fun regular(date:String,slot:CheckInSlot):CheckInWithObservations?
    @Query("SELECT EXISTS(SELECT 1 FROM checkins WHERE targetDate=:date AND slot=:slot)") suspend fun exists(date:String,slot:CheckInSlot):Boolean
    @Insert suspend fun insertCheckIn(item:CheckInEntity)
    @Update suspend fun updateCheckIn(item:CheckInEntity)
    @Query("DELETE FROM observations WHERE checkinId=:id") suspend fun clearObservations(id:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putObservations(items:List<ObservationEntity>)
    @Query("DELETE FROM checkins WHERE id=:id") suspend fun deleteCheckIn(id:String)
    @Query("SELECT * FROM blood_pressure ORDER BY measuredAt") fun observePressure():Flow<List<BloodPressureMeasurementEntity>>
    @Query("SELECT * FROM blood_pressure ORDER BY measuredAt") suspend fun pressure():List<BloodPressureMeasurementEntity>
    @Insert suspend fun insertPressure(item:BloodPressureMeasurementEntity)
    @Query("DELETE FROM blood_pressure WHERE id=:id") suspend fun deletePressure(id:String)
    @Query("SELECT * FROM cycle_events ORDER BY date") fun observeCycles():Flow<List<CycleEventEntity>>
    @Query("SELECT * FROM cycle_events ORDER BY date") suspend fun cycles():List<CycleEventEntity>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertCycle(item:CycleEventEntity):Long
    @Query("DELETE FROM cycle_events WHERE date=:date") suspend fun deleteCycle(date:String)
}

@Database(entities=[MetricDefinitionEntity::class,CheckInEntity::class,ObservationEntity::class,BloodPressureMeasurementEntity::class,CycleEventEntity::class],version=1,exportSchema=true)
@TypeConverters(Converters::class)
abstract class AppDatabase:RoomDatabase(){ abstract fun dao():AppDao
    companion object { fun create(context:Context)=Room.databaseBuilder(context,AppDatabase::class.java,"state-tracker.db").build() }
}

val DefaultMetrics=listOf(
    MetricDefinitionEntity("energy","Энергия",MetricType.SCALE,0,10,sortOrder=0,anchorLow="Вообще нет сил что-либо делать",anchorMid="Быт примерно ок",anchorHigh="Постоянно что-то делаю весь день"),
    MetricDefinitionEntity("anxiety","Тревога",MetricType.SCALE,0,10,sortOrder=1,anchorLow="Спокойно или счастливо",anchorMid="Ною как обычно, физически всё ок",anchorHigh="Всё тело напрягается, яркий комок тревоги"),
    MetricDefinitionEntity("want_to_cry","Хочется плакать",MetricType.SCALE,0,10,sortOrder=2),
    MetricDefinitionEntity("crying","Пореветь",MetricType.SCALE,0,10,sortOrder=3),
    MetricDefinitionEntity("fogginess","Ватность",MetricType.BOOLEAN,sortOrder=4),
    MetricDefinitionEntity("top_pressure_sensation","Ощущение давления сверху",MetricType.BOOLEAN,sortOrder=5),
    MetricDefinitionEntity("physical_fatigue","Физическая усталость",MetricType.BOOLEAN,sortOrder=6),
    MetricDefinitionEntity("muscle_pain","Боль в мышцах",MetricType.BOOLEAN,sortOrder=7)
)
