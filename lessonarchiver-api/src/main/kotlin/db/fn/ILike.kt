package com.lessonarchiver.db.fn

import org.jetbrains.exposed.sql.ComparisonOp
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryParameter

class ILike(
    expr1: Expression<*>,
    expr2: Expression<*>,
) : ComparisonOp(expr1, expr2, "ILIKE")

infix fun ExpressionWithColumnType<String?>.ilike(other: String): Op<Boolean> =
    ILike(
        this,
        QueryParameter(other, columnType),
    )

@JvmName("ILike_notnull")
infix fun ExpressionWithColumnType<String>.ilike(other: String): Op<Boolean> =
    ILike(
        this,
        QueryParameter(other, columnType),
    )
