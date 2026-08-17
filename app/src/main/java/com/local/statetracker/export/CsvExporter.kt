package com.local.statetracker.export

import com.local.statetracker.data.*
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CsvExporter(private val dao:AppDao){
    suspend fun write(output:OutputStream){
        val checks=dao.allCheckIns();val metrics=dao.metrics().associateBy{it.id};val pressure=dao.pressure();val cycles=dao.cycles()
        ZipOutputStream(output.buffered()).use{zip->
            fun csv(name:String,header:List<String>,rows:List<List<Any?>>){zip.putNextEntry(ZipEntry(name));zip.write((header.joinToString(",")+"\n").toByteArray());rows.forEach{row->zip.write((row.joinToString(","){quote(it)}+"\n").toByteArray())};zip.closeEntry()}
            csv("checkins.csv",listOf("checkin_id","target_date","slot","reported_for_at","created_at","updated_at","is_backfilled","is_corrected","comment"),checks.map{c->with(c.checkIn){listOf(id,targetDate,slot,instant(reportedForAt),instant(createdAt),updatedAt?.let(::instant),isBackfilled,updatedAt!=null,comment)}})
            csv("observations.csv",listOf("observation_id","checkin_id","target_date","slot","reported_for_at","checkin_created_at","checkin_updated_at","is_backfilled","is_corrected","metric_id","metric_name","metric_type","numeric_value","boolean_value"),checks.flatMap{c->c.observations.map{o->val m=metrics[o.metricId];listOf("${o.checkinId}:${o.metricId}",o.checkinId,c.checkIn.targetDate,c.checkIn.slot,instant(c.checkIn.reportedForAt),instant(c.checkIn.createdAt),c.checkIn.updatedAt?.let(::instant),c.checkIn.isBackfilled,c.checkIn.updatedAt!=null,o.metricId,m?.displayName,m?.type,o.numericValue,o.booleanValue)}})
            csv("blood_pressure.csv",listOf("measurement_id","measured_at","created_at","updated_at","systolic","diastolic","pulse","source","checkin_id"),pressure.map{listOf(it.id,instant(it.measuredAt),instant(it.createdAt),it.updatedAt?.let(::instant),it.systolic,it.diastolic,it.pulse,it.source,it.checkinId)})
            csv("cycle_events.csv",listOf("cycle_event_id","date","source","created_at","external_record_id"),cycles.map{listOf(it.id,it.date,it.source,instant(it.createdAt),it.externalRecordId)})
            csv("metrics.csv",listOf("metric_id","metric_name","metric_type","min_value","max_value","enabled","show_in_day","show_in_evening","show_in_extra","sort_order","anchor_low","anchor_mid","anchor_high"),metrics.values.sortedBy{it.sortOrder}.map{listOf(it.id,it.displayName,it.type,it.minValue,it.maxValue,it.enabled,it.showInDay,it.showInEvening,it.showInExtra,it.sortOrder,it.anchorLow,it.anchorMid,it.anchorHigh)})
        }
    }
    private fun instant(v:Long)=Instant.ofEpochMilli(v).toString()
    private fun quote(value:Any?):String{if(value==null)return "";val s=value.toString();return if(s.any{it==','||it=='"'||it=='\n'||it=='\r'})"\"${s.replace("\"","\"\"")}\"" else s}
}
