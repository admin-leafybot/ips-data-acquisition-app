package ai.indoorbrain.util

import android.content.Context
import ai.indoorbrain.R
import ai.indoorbrain.data.model.ButtonAction

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
            ButtonAction.ANOTHER_FLOOR_IN_BUILDING -> context.getString(R.string.btn_another_floor_in_building)
            ButtonAction.EXITING_BUILDING -> context.getString(R.string.btn_exiting_building)
            ButtonAction.GOING_DOWN_IN_LIFT -> context.getString(R.string.btn_going_down_in_lift)
            ButtonAction.BACK_TO_GROUND_FLOOR -> context.getString(R.string.btn_back_to_ground_floor)
            ButtonAction.LEFT_DELIVERY_BUILDING -> context.getString(R.string.btn_left_delivery_building)
            ButtonAction.ANOTHER_BUILDING_IN_SOCIETY -> context.getString(R.string.btn_another_building_in_society)
            ButtonAction.REACHED_SOCIETY_GATE -> context.getString(R.string.btn_reached_society_gate)
            ButtonAction.LEAVING_SOCIETY -> context.getString(R.string.btn_leaving_society)
        }
    }
}

