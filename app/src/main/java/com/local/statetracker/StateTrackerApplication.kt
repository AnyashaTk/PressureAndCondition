package com.local.statetracker

import android.app.Application
import com.local.statetracker.data.*
import com.local.statetracker.notifications.NotificationScheduler
import kotlinx.coroutines.*

class StateTrackerApplication:Application(){
    lateinit var container:AppContainer
    override fun onCreate(){super.onCreate();container=AppContainer(this);CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{if(container.dao.metrics().isEmpty())container.dao.putMetrics(DefaultMetrics);container.scheduler.scheduleAll()}}
}
class AppContainer(context:Application){
    val db=AppDatabase.create(context);val dao=db.dao();val checkIns=CheckInRepository(db);val pressure=PressureRepository(dao);val cycles=CycleRepository(dao);val scheduler=NotificationScheduler(context)
}
