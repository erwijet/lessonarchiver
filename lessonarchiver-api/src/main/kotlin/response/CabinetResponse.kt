package com.lessonarchiver.response

import com.lessonarchiver.db.CabinetDAO
import kotlinx.serialization.Serializable

@Serializable
class CabinetResponse(
    val id: String,
    val name: String,
    val description: String?,
)

fun CabinetDAO.toResponse() =
    let { dao ->
        CabinetResponse(
            id = dao.id.value.toString(),
            name = dao.name,
            description = dao.description,
        )
    }
