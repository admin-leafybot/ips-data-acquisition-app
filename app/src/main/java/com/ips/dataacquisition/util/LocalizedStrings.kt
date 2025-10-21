package com.ips.dataacquisition.util

import android.content.Context
import com.ips.dataacquisition.R
import com.ips.dataacquisition.data.model.ButtonAction

object LocalizedStrings {
    
    fun getButtonActionName(context: Context, action: ButtonAction): String {
        return when (action) {
            ButtonAction.ENTERED_ELEVATOR -> context.getString(R.string.btn_entered_elevator)
            ButtonAction.CLIMBING_STAIRS -> context.getString(R.string.btn_climbing_stairs)
            ButtonAction.GOING_UP_IN_LIFT -> context.getString(R.string.btn_going_up_in_lift)
            ButtonAction.COMING_DOWN_STAIRS -> context.getString(R.string.btn_coming_down_stairs)
            ButtonAction.LEFT_RESTAURANT_BUILDING -> context.getString(R.string.btn_left_restaurant_building)
            ButtonAction.ENTERED_DELIVERY_BUILDING -> context.getString(R.string.btn_entered_delivery_building)
            ButtonAction.REACHED_DELIVERY_CORRIDOR -> context.getString(R.string.btn_reached_delivery_corridor)
            ButtonAction.REACHED_DOORSTEP -> context.getString(R.string.btn_reached_doorstep)
            ButtonAction.LEFT_DOORSTEP -> context.getString(R.string.btn_left_doorstep)
            ButtonAction.GOING_DOWN_IN_LIFT -> context.getString(R.string.btn_going_down_in_lift)
            ButtonAction.LEFT_DELIVERY_BUILDING -> context.getString(R.string.btn_left_delivery_building)
            ButtonAction.REACHED_SOCIETY_GATE -> context.getString(R.string.btn_reached_society_gate)
        }
    }
}

