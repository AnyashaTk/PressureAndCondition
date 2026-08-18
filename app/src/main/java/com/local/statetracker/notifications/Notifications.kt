package com.local.statetracker.notifications

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.local.statetracker.MainActivity
import com.local.statetracker.R
import com.local.statetracker.StateTrackerApplication
import com.local.statetracker.data.CheckInSlot
import kotlinx.coroutines.*
import java.time.*

const val EXTRA_DATE="target_date";const val EXTRA_SLOT="slot";private const val CHANNEL="checkins"

class NotificationScheduler(private val context:Context){
    private val alarms=context.getSystemService(AlarmManager::class.java)
    private val preferences=context.getSharedPreferences("reminder-times",Context.MODE_PRIVATE)
    fun reminderTime(slot:CheckInSlot)=ReminderTime(preferences.getInt("${slot.name}_hour",if(slot==CheckInSlot.DAY)13 else 19),preferences.getInt("${slot.name}_minute",0))
    fun updateReminderTime(slot:CheckInSlot,hour:Int,minute:Int){preferences.edit().putInt("${slot.name}_hour",hour).putInt("${slot.name}_minute",minute).apply();scheduleAll()}
    fun scheduleAll(){schedule(CheckInSlot.DAY,reminderTime(CheckInSlot.DAY));schedule(CheckInSlot.EVENING,reminderTime(CheckInSlot.EVENING))}
    private fun schedule(slot:CheckInSlot,time:ReminderTime){
        val next=ReminderPlanner.next(ZonedDateTime.now(),time.hour,time.minute)
        scheduleAt(slot,next.toLocalDate(),next.toInstant().toEpochMilli(),slot.ordinal)
    }
    fun scheduleDebug(slot:CheckInSlot,date:LocalDate,triggerAtMillis:Long)=scheduleAt(slot,date,triggerAtMillis,100+slot.ordinal)
    private fun scheduleAt(slot:CheckInSlot,date:LocalDate,triggerAtMillis:Long,requestCode:Int){
        val intent=Intent(context,CheckInAlarmReceiver::class.java).putExtra(EXTRA_SLOT,slot.name).putExtra(EXTRA_DATE,date.toString())
        val pi=PendingIntent.getBroadcast(context,requestCode,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if(Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms())alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,triggerAtMillis,pi)
        else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,triggerAtMillis,pi)
    }
    fun cancelNotification(date:LocalDate,slot:CheckInSlot)=NotificationManagerCompat.from(context).cancel(notificationId(date,slot))
}

class CheckInAlarmReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val pending=goAsync();CoroutineScope(Dispatchers.IO).launch{try{
            val slot=runCatching{CheckInSlot.valueOf(intent.getStringExtra(EXTRA_SLOT)?:"")}.getOrNull()?:return@launch
            val date=runCatching{LocalDate.parse(intent.getStringExtra(EXTRA_DATE))}.getOrNull()?:return@launch
            val app=context.applicationContext as StateTrackerApplication
            if(ReminderDecision.shouldPost(app.container.checkIns.exists(date,slot)))show(context,date,slot)
            app.container.scheduler.scheduleAll()
        }finally{pending.finish()}}
    }
    private fun show(context:Context,date:LocalDate,slot:CheckInSlot){
        val manager=context.getSystemService(NotificationManager::class.java)
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(NotificationChannel(CHANNEL,context.getString(R.string.notification_channel),NotificationManager.IMPORTANCE_DEFAULT))
        if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        val open=Intent(context,MainActivity::class.java).putExtra(EXTRA_DATE,date.toString()).putExtra(EXTRA_SLOT,slot.name).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending=PendingIntent.getActivity(context,notificationId(date,slot),open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body=if(slot==CheckInSlot.DAY)R.string.notification_day else R.string.notification_evening
        manager.notify(notificationId(date,slot),NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_launcher).setContentTitle(context.getString(R.string.notification_title)).setContentText(context.getString(body)).setAutoCancel(true).setContentIntent(pending).build())
    }
}
class RescheduleReceiver:BroadcastReceiver(){override fun onReceive(context:Context,intent:Intent){(context.applicationContext as StateTrackerApplication).container.scheduler.scheduleAll()}}
fun notificationId(date:LocalDate,slot:CheckInSlot)=31*date.toEpochDay().hashCode()+slot.ordinal

object ReminderPlanner { fun next(now:ZonedDateTime,hour:Int,minute:Int=0):ZonedDateTime=now.toLocalDate().atTime(hour,minute).atZone(now.zone).let{if(it.isAfter(now))it else it.plusDays(1)} }
object ReminderDecision { fun shouldPost(slotFilled:Boolean)=!slotFilled }
data class ReminderTime(val hour:Int,val minute:Int){fun formatted()="%02d:%02d".format(hour,minute)}
