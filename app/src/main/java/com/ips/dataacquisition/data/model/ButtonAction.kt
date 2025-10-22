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
        // Note: Both UP and DOWN movements need floor input for multi-delivery support
        fun requiresFloorInput(action: ButtonAction, buttonPresses: List<ButtonPress> = emptyList()): Boolean {
            // Check if user is exiting (last action was EXITING_BUILDING)
            val isExiting = buttonPresses.lastOrNull()?.action == EXITING_BUILDING.name
            
            return when (action) {
                CLIMBING_STAIRS, GOING_UP_IN_LIFT -> true  // Always need floor when going up
                COMING_DOWN_STAIRS, GOING_DOWN_IN_LIFT -> !isExiting  // Only need floor if NOT exiting
                else -> false
            }
        }
        
        fun getNextActions(
            lastAction: ButtonAction?,
            hasEnteredDeliveryBuilding: Boolean,
            buttonPresses: List<ButtonPress> = emptyList()  // Add history for context
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
                
                ENTERED_ELEVATOR -> listOf(
                    GOING_UP_IN_LIFT,    // Going up to higher floor
                    GOING_DOWN_IN_LIFT   // Going down to lower floor
                )
                
                CLIMBING_STAIRS -> listOf(REACHED_DELIVERY_CORRIDOR)
                
                GOING_UP_IN_LIFT -> listOf(REACHED_DELIVERY_CORRIDOR)
                
                // Note: COMING_DOWN_STAIRS and GOING_DOWN_IN_LIFT are handled below
                // in a context-aware manner (checking if exiting or going to another floor)
                
                REACHED_DELIVERY_CORRIDOR -> listOf(REACHED_DOORSTEP)
                
                REACHED_DOORSTEP -> listOf(LEFT_DOORSTEP)
                
                // After delivery: Either go to another floor OR exit building
                LEFT_DOORSTEP -> {
                    // Find the last vertical movement (stairs/lift) that had a floor number
                    val lastFloorMovement = buttonPresses.findLast { press ->
                        val action = press.action
                        (action == CLIMBING_STAIRS.name || 
                         action == GOING_UP_IN_LIFT.name || 
                         action == COMING_DOWN_STAIRS.name || 
                         action == GOING_DOWN_IN_LIFT.name) &&
                        press.floorIndex != null
                    }
                    
                    val isOnGroundFloor = lastFloorMovement?.floorIndex == 0
                    
                    if (isOnGroundFloor) {
                        // Already on ground floor - can only exit building or go to another building
                        listOf(
                            LEFT_DELIVERY_BUILDING,      // Exit this building
                            ANOTHER_BUILDING_IN_SOCIETY  // Go to another building in society
                        )
                    } else {
                        // On upper floor - can go to another floor or start exiting
                        listOf(
                            ANOTHER_FLOOR_IN_BUILDING,  // Stay in building, deliver to another floor
                            EXITING_BUILDING           // Done with this building
                        )
                    }
                }
                
                // Loop back to elevator/stairs for next floor in same building
                // User can go UP or DOWN to another floor
                ANOTHER_FLOOR_IN_BUILDING -> listOf(
                    ENTERED_ELEVATOR,    // Can go up or down
                    CLIMBING_STAIRS,     // Going up
                    COMING_DOWN_STAIRS   // Going down to lower floor
                )
                
                // Exiting building - go down
                EXITING_BUILDING -> listOf(
                    GOING_DOWN_IN_LIFT,
                    COMING_DOWN_STAIRS
                )
                
                // After going down: Either reached another delivery floor OR ground floor
                COMING_DOWN_STAIRS, GOING_DOWN_IN_LIFT -> {
                    // Check if previous action was EXITING_BUILDING or ANOTHER_FLOOR_IN_BUILDING
                    val previousAction = buttonPresses.lastOrNull()?.action
                    val isExiting = previousAction == EXITING_BUILDING.name
                    val isGoingToAnotherFloor = previousAction == ANOTHER_FLOOR_IN_BUILDING.name
                    
                    if (isExiting) {
                        // When exiting, only show ground floor option (automatically go to ground)
                        listOf(BACK_TO_GROUND_FLOOR)
                    } else if (isGoingToAnotherFloor) {
                        // When going to another floor for delivery, go straight to delivery corridor
                        listOf(REACHED_DELIVERY_CORRIDOR)
                    } else {
                        // Fallback: let user choose
                        listOf(
                            REACHED_DELIVERY_CORRIDOR,  // Delivery on this floor
                            BACK_TO_GROUND_FLOOR        // Just passing through to exit
                        )
                    }
                }
                
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

