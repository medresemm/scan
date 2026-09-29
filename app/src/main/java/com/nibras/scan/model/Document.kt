package com.nibras.scan.model

import java.io.File

data class Document(
    val name: String,
    val file: File,
    val pageCount: Int,
    val createdAt: Long
)
