package com.nibras.scan.model

import java.io.File
import java.util.UUID

enum class FilterType {
    ORIGINAL, AUTO, BLACK_WHITE, GRAYSCALE
}

/**
 * One page inside the current scanning session.
 *
 * [originalFile] always points at the perspective-corrected (cropped) image, straight
 * out of the camera/crop step, untouched by filters. Filters and rotation are applied
 * on-the-fly for preview/export so the original data is never destructively lost while
 * the user is still editing.
 */
data class Page(
    val id: String = UUID.randomUUID().toString(),
    var originalFile: File,
    var rotationDegrees: Int = 0,
    var filter: FilterType = FilterType.ORIGINAL
)
