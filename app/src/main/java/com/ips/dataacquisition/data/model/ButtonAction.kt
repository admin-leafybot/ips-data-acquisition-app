package com.ips.dataacquisition.data.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ips.dataacquisition.R

enum class ButtonAction(val displayName: String) {
    // DISABLED: Session now starts at LEFT_RESTAURANT_BUILDING
    // ENTERED_RESTAURANT_BUILDING("Entered Restaurant Building"),
    ENTERED_ELEVATOR("Entered Elevator"),
    CLIMBING_STAIRS("Climbing Stairs"),  // Renamed from CLIMBING_STAIRS_3_FLOORS
    GOING_UP_IN_LIFT("Going up in Lift"),  // Renamed from GOING_UP_8_FLOORS_IN_LIFT
    // DISABLED: REACHED_RESTAURANT_CORRIDOR("Reached Restaurant Corridor"),
    // DISABLED: REACHED_RESTAURANT("Reached Restaurant"),
    // DISABLED: LEFT_RESTAURANT("Left Restaurant"),
    COMING_DOWN_STAIRS("Coming Down Stairs"),  // Renamed from COMING_DOWN_3_FLOORS
    LEFT_RESTAURANT_BUILDING("Left Restaurant Building"),  // SESSION START
    ENTERED_DELIVERY_BUILDING("Entered Delivery Building"),
    REACHED_DELIVERY_CORRIDOR("Reached Delivery Corridor"),
    REACHED_DOORSTEP("Reached Doorstep"),
    LEFT_DOORSTEP("Left Doorstep"),
    GOING_DOWN_IN_LIFT("Going down in Lift"),  // Renamed from GOING_DOWN_8_FLOORS_IN_LIFT
    LEFT_DELIVERY_BUILDING("Left Delivery Building");  // SESSION END

    companion object {
        // Actions that require floor number input
        fun requiresFloorInput(action: ButtonAction): Boolean {
            return action in listOf(
                CLIMBING_STAIRS,
                GOING_UP_IN_LIFT,
                COMING_DOWN_STAIRS,
                GOING_DOWN_IN_LIFT
            )
        }
        
        fun getNextActions(
            lastAction: ButtonAction?,
            hasEnteredDeliveryBuilding: Boolean
        ): List<ButtonAction> {
            // ============================================================
            // SIMPLIFIED FLOW (Current Active)
            // Session starts at LEFT_RESTAURANT_BUILDING
            // Session ends at LEFT_DELIVERY_BUILDING
            // ============================================================
            return when (lastAction) {
                // START: Session begins at LEFT_RESTAURANT_BUILDING
                null, LEFT_DELIVERY_BUILDING -> listOf(LEFT_RESTAURANT_BUILDING)
                
                LEFT_RESTAURANT_BUILDING -> listOf(ENTERED_DELIVERY_BUILDING)
                
                ENTERED_DELIVERY_BUILDING -> listOf(
                    ENTERED_ELEVATOR,
                    CLIMBING_STAIRS
                )
                
                ENTERED_ELEVATOR -> listOf(GOING_UP_IN_LIFT)
                
                CLIMBING_STAIRS -> listOf(REACHED_DELIVERY_CORRIDOR)
                
                GOING_UP_IN_LIFT -> listOf(REACHED_DELIVERY_CORRIDOR)
                
                REACHED_DELIVERY_CORRIDOR -> listOf(REACHED_DOORSTEP)
                
                REACHED_DOORSTEP -> listOf(LEFT_DOORSTEP)
                
                LEFT_DOORSTEP -> listOf(
                    GOING_DOWN_IN_LIFT,
                    COMING_DOWN_STAIRS
                )
                
                COMING_DOWN_STAIRS -> listOf(LEFT_DELIVERY_BUILDING)
                
                GOING_DOWN_IN_LIFT -> listOf(LEFT_DELIVERY_BUILDING)
                
                // END: Session ends at LEFT_DELIVERY_BUILDING
            }
            
            // ============================================================
            // ORIGINAL FULL FLOW (Commented Out - Can be restored)
            // Uncomment below and comment out above to restore full flow
            // ============================================================
            /*
            return when (lastAction) {
                null, LEFT_DELIVERY_BUILDING -> listOf(ENTERED_RESTAURANT_BUILDING)
                
                ENTERED_RESTAURANT_BUILDING -> listOf(
                    ENTERED_ELEVATOR,
                    CLIMBING_STAIRS_3_FLOORS
                )
                
                ENTERED_ELEVATOR -> listOf(GOING_UP_8_FLOORS_IN_LIFT)
                
                CLIMBING_STAIRS_3_FLOORS -> {
                    if (hasEnteredDeliveryBuilding) {
                        listOf(REACHED_DELIVERY_CORRIDOR)
                    } else {
                        listOf(REACHED_RESTAURANT_CORRIDOR)
                    }
                }
                
                GOING_UP_8_FLOORS_IN_LIFT -> {
                    if (hasEnteredDeliveryBuilding) {
                        listOf(REACHED_DELIVERY_CORRIDOR)
                    } else {
                        listOf(REACHED_RESTAURANT_CORRIDOR)
                    }
                }
                
                REACHED_RESTAURANT_CORRIDOR -> listOf(REACHED_RESTAURANT)
                
                REACHED_RESTAURANT -> listOf(LEFT_RESTAURANT)
                
                LEFT_RESTAURANT -> listOf(
                    GOING_DOWN_8_FLOORS_IN_LIFT,
                    COMING_DOWN_3_FLOORS
                )
                
                COMING_DOWN_3_FLOORS -> {
                    if (hasEnteredDeliveryBuilding) {
                        listOf(LEFT_DELIVERY_BUILDING)
                    } else {
                        listOf(LEFT_RESTAURANT_BUILDING)
                    }
                }
                
                GOING_DOWN_8_FLOORS_IN_LIFT -> {
                    if (hasEnteredDeliveryBuilding) {
                        listOf(LEFT_DELIVERY_BUILDING)
                    } else {
                        listOf(LEFT_RESTAURANT_BUILDING)
                    }
                }
                
                LEFT_RESTAURANT_BUILDING -> listOf(ENTERED_DELIVERY_BUILDING)
                
                ENTERED_DELIVERY_BUILDING -> listOf(
                    ENTERED_ELEVATOR,
                    CLIMBING_STAIRS_3_FLOORS
                )
                
                REACHED_DELIVERY_CORRIDOR -> listOf(REACHED_DOORSTEP)
                
                REACHED_DOORSTEP -> listOf(LEFT_DOORSTEP)
                
                LEFT_DOORSTEP -> listOf(LEFT_DELIVERY_BUILDING)
            }
            */
        }
    }
}

// Composable extension to get localized button name
@Composable
fun ButtonAction.localizedName(): String {
    val context = LocalContext.current
    return when (this) {
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
    }
}

