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
    REACHED_SOCIETY_GATE("Reached Society Gate"),  // Triggers continuous logging
    ENTERED_DELIVERY_BUILDING("Entered Delivery Building"),
    REACHED_DELIVERY_CORRIDOR("Reached Delivery Corridor"),
    REACHED_DOORSTEP("Reached Doorstep"),
    LEFT_DOORSTEP("Left Doorstep"),
    ANOTHER_FLOOR_IN_BUILDING("Going to Another Floor"),  // Loop back to elevator/stairs
    EXITING_BUILDING("Exiting Building"),  // Go down to ground
    GOING_DOWN_IN_LIFT("Going down in Lift"),  // Renamed from GOING_DOWN_8_FLOORS_IN_LIFT
    BACK_TO_GROUND_FLOOR("Back to Ground Floor"),  // At ground level
    LEFT_DELIVERY_BUILDING("Left Current Building"),  // Exit this building
    ANOTHER_BUILDING_IN_SOCIETY("Going to Another Building"),  // Next tower in society
    LEAVING_SOCIETY("Leaving Society");  // SESSION END

    companion object {
        // Actions that require floor number input
        // Note: Only UPWARD movements need floor input (going down is always to ground)
        fun requiresFloorInput(action: ButtonAction): Boolean {
            return action in listOf(
                CLIMBING_STAIRS,      // Going up - need to know which floor
                GOING_UP_IN_LIFT      // Going up - need to know which floor
                // REMOVED: COMING_DOWN_STAIRS - always going to ground
                // REMOVED: GOING_DOWN_IN_LIFT - always going to ground
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
                null, LEAVING_SOCIETY -> listOf(LEFT_RESTAURANT_BUILDING)
                
                LEFT_RESTAURANT_BUILDING -> listOf(REACHED_SOCIETY_GATE)
                
                REACHED_SOCIETY_GATE -> listOf(ENTERED_DELIVERY_BUILDING)
                
                ENTERED_DELIVERY_BUILDING -> listOf(
                    ENTERED_ELEVATOR,
                    CLIMBING_STAIRS
                )
                
                ENTERED_ELEVATOR -> listOf(GOING_UP_IN_LIFT)
                
                CLIMBING_STAIRS -> listOf(REACHED_DELIVERY_CORRIDOR)
                
                GOING_UP_IN_LIFT -> listOf(REACHED_DELIVERY_CORRIDOR)
                
                REACHED_DELIVERY_CORRIDOR -> listOf(REACHED_DOORSTEP)
                
                REACHED_DOORSTEP -> listOf(LEFT_DOORSTEP)
                
                // After delivery: Either go to another floor OR exit building
                LEFT_DOORSTEP -> listOf(
                    ANOTHER_FLOOR_IN_BUILDING,  // Stay in building, deliver to another floor
                    EXITING_BUILDING           // Done with this building
                )
                
                // Loop back to elevator/stairs for next floor in same building
                ANOTHER_FLOOR_IN_BUILDING -> listOf(
                    ENTERED_ELEVATOR,
                    CLIMBING_STAIRS
                )
                
                // Exiting building - go down
                EXITING_BUILDING -> listOf(
                    GOING_DOWN_IN_LIFT,
                    COMING_DOWN_STAIRS
                )
                
                // Reached ground floor
                COMING_DOWN_STAIRS, GOING_DOWN_IN_LIFT -> listOf(
                    BACK_TO_GROUND_FLOOR
                )
                
                // At ground floor - exit this building
                BACK_TO_GROUND_FLOOR -> listOf(
                    LEFT_DELIVERY_BUILDING
                )
                
                // Left building - either go to another tower OR leave society
                LEFT_DELIVERY_BUILDING -> listOf(
                    ANOTHER_BUILDING_IN_SOCIETY,  // Next tower/building in society
                    LEAVING_SOCIETY              // All done, exit society (END SESSION)
                )
                
                // Loop back to enter next building
                ANOTHER_BUILDING_IN_SOCIETY -> listOf(ENTERED_DELIVERY_BUILDING)
                
                // END: Session ends at LEAVING_SOCIETY
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
        ButtonAction.REACHED_SOCIETY_GATE -> context.getString(R.string.btn_reached_society_gate)
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
        ButtonAction.LEAVING_SOCIETY -> context.getString(R.string.btn_leaving_society)
    }
}

