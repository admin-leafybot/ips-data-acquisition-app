package com.ips.dataacquisition.data.model

enum class ButtonAction(val displayName: String) {
    ENTERED_RESTAURANT_BUILDING("Entered Restaurant Building"),
    ENTERED_ELEVATOR("Entered Elevator"),
    CLIMBING_STAIRS_3_FLOORS("Climbing Stairs 3 floors"),
    GOING_UP_8_FLOORS_IN_LIFT("Going up 8 floors in Lift"),
    REACHED_RESTAURANT_CORRIDOR("Reached Restaurant Corridor"),
    REACHED_RESTAURANT("Reached Restaurant"),
    LEFT_RESTAURANT("Left Restaurant"),
    COMING_DOWN_3_FLOORS("Coming Down 3 floors"),
    LEFT_RESTAURANT_BUILDING("Left Restaurant Building"),
    ENTERED_DELIVERY_BUILDING("Entered Delivery Building"),
    REACHED_DELIVERY_CORRIDOR("Reached Delivery Corridor"),
    REACHED_DOORSTEP("Reached Doorstep"),
    LEFT_DOORSTEP("Left Doorstep"),
    GOING_DOWN_8_FLOORS_IN_LIFT("Going down 8 floors in Lift"),
    LEFT_DELIVERY_BUILDING("Left Delivery Building");

    companion object {
        fun getNextActions(
            lastAction: ButtonAction?,
            hasEnteredDeliveryBuilding: Boolean
        ): List<ButtonAction> {
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
        }
    }
}

