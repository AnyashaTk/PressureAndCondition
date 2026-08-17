package com.local.statetracker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

data class ImportedPeriod(val id:String,val start:Instant)
interface HealthConnectDataSource{val permissions:Set<String>;fun availability():Int;suspend fun hasPermission():Boolean;suspend fun read():List<ImportedPeriod>}
class AndroidHealthConnectDataSource(private val context:Context):HealthConnectDataSource{
    override val permissions=setOf(HealthPermission.getReadPermission(MenstruationPeriodRecord::class))
    override fun availability()=HealthConnectClient.getSdkStatus(context)
    private fun client()=HealthConnectClient.getOrCreate(context)
    override suspend fun hasPermission()=client().permissionController.getGrantedPermissions().containsAll(permissions)
    override suspend fun read()=client().readRecords(ReadRecordsRequest(MenstruationPeriodRecord::class,TimeRangeFilter.before(Instant.now()))).records.map{ImportedPeriod(it.metadata.id,it.startTime)}
}
