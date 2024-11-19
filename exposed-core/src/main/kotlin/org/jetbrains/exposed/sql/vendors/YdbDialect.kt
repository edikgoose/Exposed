package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.GroupConcat
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.append
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.transactions.TransactionManager

class YdbDialect(override val name: String = dialectName) :
    VendorDialect(dialectName, YdbDataTypeProvider, YdbFunctionProvider) {
    override val supportsCreateSequence = false
    override val supportsRestrictReferenceOption = false
    override val supportsSetDefaultReferenceOption = false
    override val supportsForeignKeyConstraint = false
    override val supportsAutoIncReturn: Boolean = false

    override fun primaryKeyConstraint(pkName: String?, columns: List<Column<*>>): String {
        exposedLogger.warn("YDB does not support custom primary key naming. The name of the constraint will be generated automatically.")
        return columns
            .joinToString(
                prefix = "PRIMARY KEY (",
                postfix = ")",
                transform = TransactionManager.current()::identity
            )
    }

    companion object : DialectNameProvider("YDB")
}

internal object YdbDataTypeProvider : DataTypeProvider() {
    override fun byteType() = "Int8"
    override fun ubyteType() = "Uint8"

    override fun shortType() = "Int16"
    override fun ushortType() = "Uint16"

    override fun integerType() = "Int32"
    override fun uintegerType() = "Uint32"

    override fun integerAutoincType() = "Serial"
    override fun uintegerAutoincType() = "Serial"

    override fun longType() = "Int64"
    override fun ulongType() = "Uint64"

    override fun longAutoincType() = "BigSerial"
    override fun ulongAutoincType() = "BigSerial"

    override fun doubleType() = "Double"

    override fun textType() = "String"
    override fun mediumTextType() = textType()
    override fun largeTextType() = textType()
    override fun varcharType(colLength: Int) =
        throw UnsupportedByDialectException("YDB does not support varchar type", currentDialect)

    override fun binaryType() = "String"
    override fun binaryType(length: Int) =
        throw UnsupportedByDialectException("YDB does not support limited binary data type", currentDialect)

    override fun blobType() = binaryType()

    override fun uuidType() = "Uuid"

    override fun dateTimeType() = "Timestamp"
    override fun timestampWithTimeZoneType() = "TzTimestamp"
    override fun timeType() = "Timestamp"

    override fun booleanType() = "Bool"

    override fun hexToDb(hexString: String): String = "X'$hexString'"
}

internal object YdbFunctionProvider : FunctionProvider() {
    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        throw UnsupportedByDialectException("YDB does not support GROUP_CONCAT", currentDialect)
    }

    override fun <T : String?> locate(queryBuilder: QueryBuilder, expr: Expression<T>, substring: String) =
        queryBuilder {
            append("FIND(", expr, ", '", substring, "')")
        }
}
