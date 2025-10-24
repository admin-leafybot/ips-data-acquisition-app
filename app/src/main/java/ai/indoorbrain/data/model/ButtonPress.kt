package ai.indoorbrain.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "button_presses")
data class ButtonPress(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "session_id")
    @SerializedName("SessionId")
    val sessionId: String,
    
    @ColumnInfo(name = "action")
    @SerializedName("Action")
    val action: String,
    
    @ColumnInfo(name = "timestamp")
    @SerializedName("Timestamp")
    val timestamp: Long,
    
    @ColumnInfo(name = "floor_index")
    @SerializedName("FloorIndex")
    val floorIndex: Int? = null,  // Optional: only for stairs/lift events
    
    @ColumnInfo(name = "is_synced")
    @SerializedName("IsSynced")
    val isSynced: Boolean = false
)

