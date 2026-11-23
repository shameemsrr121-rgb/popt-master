package com.example.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.AppSettings
import com.example.data.entity.PurchaseOrderEntity
import com.example.data.entity.PurchasePlanNoteEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

object PurchasePlanAlarmScheduler {
    private const val TAG = "PurchasePlanScheduler"
    const val ACTION_TRIGGER_PLAN_REMINDER = "com.example.ACTION_TRIGGER_PLAN_REMINDER"
    const val ACTION_TRIGGER_PO_REMINDER = "com.example.ACTION_TRIGGER_PO_REMINDER"

    const val EXTRA_PLAN_ID = "extra_plan_id"
    const val EXTRA_PLAN_TITLE = "extra_plan_title"
    const val EXTRA_PLAN_DATE_MILLIS = "extra_plan_date_millis"
    const val EXTRA_PLAN_LOCATION = "extra_plan_location"
    const val EXTRA_PLAN_NOTES = "extra_plan_notes"
    const val EXTRA_PLAN_BUDGET = "extra_plan_budget"
    const val EXTRA_PLAN_CATEGORY = "extra_plan_category"
    const val EXTRA_PLAN_PRIORITY = "extra_plan_priority"
    const val EXTRA_PLAN_STATUS = "extra_plan_status"
    const val EXTRA_DAYS_LEFT = "extra_days_left"

    const val EXTRA_PO_ID = "extra_po_id"
    const val EXTRA_PO_NUMBER = "extra_po_number"
    const val EXTRA_PO_SUPPLIER_NAME = "extra_po_supplier_name"
    const val EXTRA_PO_TOTAL_AMOUNT = "extra_po_total_amount"
    const val EXTRA_PO_DELIVERY_DATE = "extra_po_delivery_date"
    const val EXTRA_PO_STATUS = "extra_po_status"
    const val EXTRA_PO_LOCATION = "extra_po_location"
    const val EXTRA_PO_IS_OVERDUE = "extra_po_is_overdue"

    /**
     * Schedules an alarm reminder for a specific purchase plan.
     */
    fun schedulePlanReminder(context: Context, plan: PurchasePlanNoteEntity) {
        if (plan.status == "COMPLETED" || plan.status == "CONVERTED_TO_PO") {
            cancelPlanReminder(context, plan.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Calculate reminder trigger time (e.g. 9:00 AM on (plannedDate - reminderDaysBefore))
        val calendar = Calendar.getInstance().apply {
            timeInMillis = plan.plannedDateMillis
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -plan.reminderDaysBefore)
        }

        val triggerAtMillis = calendar.timeInMillis
        val now = System.currentTimeMillis()

        // Calculate days left relative to planned date
        val daysLeft = ((plan.plannedDateMillis - now) / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0)

        // If reminder time has passed but plan date is still in the future or today, trigger notification now
        if (triggerAtMillis <= now && plan.plannedDateMillis >= now - TimeUnit.DAYS.toMillis(1)) {
            Log.d(TAG, "Plan '${plan.title}' reminder time already passed or is today; posting immediate notification")
            val settings = com.example.data.AppSettingsManager.getInstance(context).settingsState.value
            NotificationAlertService.postPurchasePlanNotification(context, plan, daysLeft, settings)
            return
        }

        val intent = Intent(context, PurchasePlanAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_PLAN_REMINDER
            putExtra(EXTRA_PLAN_ID, plan.id)
            putExtra(EXTRA_PLAN_TITLE, plan.title)
            putExtra(EXTRA_PLAN_DATE_MILLIS, plan.plannedDateMillis)
            putExtra(EXTRA_PLAN_LOCATION, plan.location)
            putExtra(EXTRA_PLAN_NOTES, plan.notes)
            putExtra(EXTRA_PLAN_BUDGET, plan.estimatedBudget)
            putExtra(EXTRA_PLAN_CATEGORY, plan.categoryOrSupplier)
            putExtra(EXTRA_PLAN_PRIORITY, plan.priority)
            putExtra(EXTRA_PLAN_STATUS, plan.status)
            putExtra(EXTRA_DAYS_LEFT, plan.reminderDaysBefore.toLong())
        }

        val requestCode = (plan.id.toInt() + 20000)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
            Log.d(TAG, "Scheduled reminder for Plan '${plan.title}' at ${calendar.time}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for plan ${plan.id}", e)
        }
    }

    /**
     * Cancels an existing purchase plan reminder.
     */
    fun cancelPlanReminder(context: Context, planId: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, PurchasePlanAlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER_PLAN_REMINDER
            }
            val requestCode = (planId.toInt() + 20000)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling plan reminder $planId", e)
        }
    }

    /**
     * Schedules a PO delivery alert alarm.
     */
    fun schedulePOReminder(context: Context, po: PurchaseOrderEntity, daysBefore: Int) {
        if (po.status != "ISSUED" && po.status != "PARTIALLY_RECEIVED") {
            cancelPOReminder(context, po.id)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val calendar = Calendar.getInstance().apply {
            timeInMillis = po.expectedDeliveryDateMillis
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -daysBefore)
        }

        val triggerAtMillis = calendar.timeInMillis
        val now = System.currentTimeMillis()
        val daysLeft = ((po.expectedDeliveryDateMillis - now) / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0)
        val isOverdue = po.expectedDeliveryDateMillis < now

        if (triggerAtMillis <= now) {
            val settings = com.example.data.AppSettingsManager.getInstance(context).settingsState.value
            NotificationAlertService.postPODeliveryNotification(context, po, daysLeft, isOverdue, settings)
            return
        }

        val intent = Intent(context, PurchasePlanAlarmReceiver::class.java).apply {
            action = ACTION_TRIGGER_PO_REMINDER
            putExtra(EXTRA_PO_ID, po.id)
            putExtra(EXTRA_PO_NUMBER, po.poNumber)
            putExtra(EXTRA_PO_SUPPLIER_NAME, po.supplierName)
            putExtra(EXTRA_PO_TOTAL_AMOUNT, po.totalAmount)
            putExtra(EXTRA_PO_DELIVERY_DATE, po.expectedDeliveryDateMillis)
            putExtra(EXTRA_PO_STATUS, po.status)
            putExtra(EXTRA_PO_LOCATION, po.location)
            putExtra(EXTRA_PO_IS_OVERDUE, isOverdue)
            putExtra(EXTRA_DAYS_LEFT, daysLeft)
        }

        val requestCode = (po.id.toInt() + 10000)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule PO alarm for ${po.id}", e)
        }
    }

    fun cancelPOReminder(context: Context, poId: Long) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, PurchasePlanAlarmReceiver::class.java).apply {
                action = ACTION_TRIGGER_PO_REMINDER
            }
            val requestCode = (poId.toInt() + 10000)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling PO reminder $poId", e)
        }
    }

    /**
     * Reschedules all upcoming purchase plans and PO reminders.
     */
    fun rescheduleAll(
        context: Context,
        plans: List<PurchasePlanNoteEntity>,
        pos: List<PurchaseOrderEntity>,
        settings: AppSettings
    ) {
        if (!settings.notificationsEnabled) return

        if (settings.purchasePlanAlertsEnabled) {
            for (plan in plans) {
                if (plan.status != "COMPLETED" && plan.status != "CONVERTED_TO_PO") {
                    schedulePlanReminder(context, plan)
                }
            }
        }

        if (settings.poDeliveryAlertsEnabled) {
            for (po in pos) {
                if (po.status == "ISSUED" || po.status == "PARTIALLY_RECEIVED") {
                    schedulePOReminder(context, po, settings.poDeliveryAlertDaysBefore)
                }
            }
        }
    }
}
