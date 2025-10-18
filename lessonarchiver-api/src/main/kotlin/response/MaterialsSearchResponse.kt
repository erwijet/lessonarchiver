package com.lessonarchiver.response

import kotlinx.serialization.Serializable

@Serializable
data class MaterialsSearchResponse(
    val q: String,
    val notes: List<NoteResponse>,
    val files: List<FileResponse>,
)
