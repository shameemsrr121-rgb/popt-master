package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.AppSettingsManager
import com.example.data.database.AppDatabase
import com.example.data.entity.PurchaseOrderEntity
import com.example.data.entity.PurchasePlanNoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PurchasePlanAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PurchasePlanReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "onReceive action: $action")

        val settingsManager = AppSettingsManager.getInstance(context)
        val settings = settingsManager.settingsState.value

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Device rebooted, rescheduling pending purchase plan and PO alerts...")
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val db = AppDatabase.getInstance(context)
                        val plans = db.purchasePlanNoteDao().getAllNotesDirect()
                        val pos = db.purchaseOrderDao().getAllPurchaseOrdersDirect()
                        PurchasePlanAlarmScheduler.rescheduleAll(context, plans, pos, settings)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to reschedule on boot", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            PurchasePlanAlarmScheduler.ACTION_TRIGGER_PLAN_REMINDER -> {
                val planId = intent.getLongExtra(PurchasePlanAlarmScheduler.EXTRA_PLAN_ID, 0L)
                val title = intent.getStringExtra(PurchasePlanAlarmScheduler.EXTRA_PLAN_TITLE) ?: "Purchase Sourcing Plan"
                val dateMillis = intent.getLongExtra(PurchasePlanAlarmScheduler.EXTRA_PLAN_DATE_MILLIS, System.currentTimeMillis())
                val location = intent.getStringExtra(PurchasePlanAlarmScheduler.EXTRA_PLAN_LOCATION) ?: ""
                val notes = intent.getStringExtra(PurchasePlanAlarmScheduler.EXTRA_PLAN_NOTES) ?: ""
                val budget = intent.getDoubleExtra(PurchasePlanAlarmScheduler.EXTRA_PLAN_BUDGET, 0.0)
                val category = intent.getStringExtra(PurchasePlanAlarmScheduler.EXTRA_PLAN_CATEGORY) ?: ""
                val priority = intent.getStringExtra(PurchasePlanAlarmScheduler.EXTRA_PLAN_PRIORITY) ?: "MEDIUM"
                val status = intent.getStringExtra(PurchasePlanAlarmScheduler.EXTRA_PLAN_STATUS) ?: "PLANNED"
                val daysLeft = intent.getLongExtra(PurchasePlanAlarmScheduler.EXTRA_DAYS_LEFT, 0L)

                val plan = PurchasePlanNoteEntity(
                    id = planId,
                    title = title,
                    plannedDateMillis = dateMillis,
                    location = location,
                    notes = notes,
                    estimatedBudget = budget,
                    categoryOrSupplier = category,
                    priority = priority,
                    status = status
                )

                Log.d(TAG, "Posting Purchase Plan Notification for: ${plan.title}")
                NotificationAlertService.postPurchasePlanNotification(context, plan, daysLeft, settings)
            }

            PurchasePlanAlarmScheduler.ACTION_TRIGGER_PO_REMINDER -> {
                val poId = intent.getLongExtra(PurchasePlanAlarmScheduler.EXTRA_PO_ID, 0L)
                val poNumber = intent.getStringExtra(PurchasePlanAlarmScheduler.EXTRA_PO_NUMBER) ?: "PO-$poId"
                val supplierName = intent.getStringExtra(PurchasePlanAlarmScheduler.EXTRA_PO_SUPPLIER_NAME) ?: "Supplier"
                val totalAmount = intent.getDoubleExtra(PurchasePlanAlarmScheduler.EXTRA_PO_TOTAL_AMOUNT, 0.0)
                val deliveryDate = intent.getLongExtra(PurchasePlanAlarmScheduler.EXTRA_PO_DELIVERY_DATE, System.currentTimeMillis())
                val poStatus = intent.getStringExtra(PurchasePlanAlarmScheduler.EXTRA_PO_STATUS) ?: "ISSUED"
                val poLocation = intent.getStringExtra(PurchasePlanAlarmScheduler.EXTRA_PO_LOCATION) ?: "Central"
                val isOverdue = intent.getBooleanExtra(PurchasePlanAlarmScheduler.EXTRA_PO_IS_OVERDUE, false)
                val daysLeft = intent.getLongExtra(PurchasePlanAlarmScheduler.EXTRA_DAYS_LEFT, 0L)

                val po = PurchaseOrderEntity(
                    id = poId,
                    poNumber = poNumber,
                    supplierId = 1L,
                    supplierName = supplierName,
                    orderDateMillis = deliveryDate - 86400000L,
                    expectedDeliveryDateMillis = deliveryDate,
                    paymentTerms = "Net 30",
                    status = poStatus,
                    subtotal = totalAmount * 0.95,
                    taxAmount = totalAmount * 0.05,
                    totalAmount = totalAmount,
                    location = poLocation
                )

                Log.d(TAG, "Posting PO Delivery Notification for: ${po.poNumber}")
                NotificationAlertService.postPODeliveryNotification(context, po, daysLeft, isOverdue, settings)
            }
        }
    }
}
