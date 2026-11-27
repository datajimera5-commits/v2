package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class GenerationType {
    FIRE_VIDEO,
    IMAGE,
    MOTION
}

enum class FireOrderStatus(val label: String) {
    UPLOADING("Uploading Assets…"),
    RENDERING("Seedance 2.5 is rendering…"),
    HOLD("On Hold"),
    RETRY("Retry Required"),
    CANCELLED("Cancelled"),
    FAILED("Failed"),
    READY("Ready"),
    DOWNLOADED("Downloaded")
}

@Entity(tableName = "generations")
data class GenerationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderUid: String,
    val type: GenerationType,
    val prompt: String,
    val enhancedPrompt: String = "",
    val modelName: String,
    val aspectRatio: String = "16:9",
    val durationSeconds: Int = 10,
    val referenceUris: String = "",
    val referenceCount: Int = 0,
    val motionPreset: String = "",
    val motionIntensity: Int = 50,
    val status: FireOrderStatus = FireOrderStatus.READY,
    val adminMessage: String = "",
    val outputTitle: String = "",
    val outputSummary: String = "",
    val localFilePath: String = "",
    val remoteMediaUrl: String = "",
    val isSamplePlaceholder: Boolean = true,
    val isUpscaled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
