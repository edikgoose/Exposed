package org.jetbrains.exposed.sql.vendors

import org.jetbrains.exposed.exceptions.UnsupportedByDialectException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Function
import org.jetbrains.exposed.sql.transactions.TransactionManager

class YdbDialect(override val name: String = dialectName) :
    VendorDialect(dialectName, YdbDataTypeProvider, YdbFunctionProvider) {
    override val supportsCreateSequence = false
    override val supportsRestrictReferenceOption = false
    override val supportsSetDefaultReferenceOption = false
    override val supportsForeignKeyConstraint = false
    override val supportsAutoIncReturn: Boolean = false
    override val supportsCollate: Boolean = false
    override val supportsOnlyIdentifiersInGeneratedKeys: Boolean = true

    override fun primaryKeyConstraint(pkName: String?, columns: List<Column<*>>): String {
        exposedLogger.warn("YDB does not support custom primary key naming. The name of the constraint will be generated automatically.")
        return columns
            .joinToString(
                prefix = "PRIMARY KEY (",
                postfix = ")",
                transform = TransactionManager.current()::identity
            )
    }

    override fun isAllowedAsColumnDefault(e: Expression<*>) = false

    override fun createIndex(index: Index): String {
        val t = TransactionManager.current()
        val quotedTableName = t.identity(index.table)
        val quotedIndexName = t.db.identifierManager.cutIfNecessaryAndQuote(index.indexName)
        val keyFields = index.columns.plus(index.functions ?: emptyList())
        val fieldsList = keyFields.joinToString(prefix = "(", postfix = ")") {
            when (it) {
                is Column<*> -> t.identity(it)
                is Function<*> -> it.toString()
                // returned by existingIndices() mapping String metadata to stringLiteral()
                is LiteralOp<*> -> it.value.toString().trim('"')
                else -> {
                    exposedLogger.warn("Unexpected defining key field will be passed as String: $it")
                    it.toString()
                }
            }
        }
        val includesOnlyColumns = index.functions?.isEmpty() != false

        if (!includesOnlyColumns) {
            exposedLogger.warn("YDB does not support index functions. The index will not be created.")
            return ""
        }

        return buildString {
            append("ALTER TABLE $quotedTableName ADD INDEX $quotedIndexName GLOBAL ")
            if (index.unique) {
                append("UNIQUE ")
            }
            append("ON $fieldsList")
        }
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
    override fun varcharType(colLength: Int) = textType()
    override fun charTextType(): String = textType()


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

    override fun precessOrderByClause(queryBuilder: QueryBuilder, expression: Expression<*>, sortOrder: SortOrder) {
        when (sortOrder) {
            SortOrder.ASC, SortOrder.DESC -> super.precessOrderByClause(queryBuilder, expression, sortOrder)
            else -> throw UnsupportedByDialectException("YDB does not support sorting by $sortOrder", currentDialect)
        }
    }
}

internal object YdbFunctionProvider : FunctionProvider() {
    override fun <T : String?> groupConcat(expr: GroupConcat<T>, queryBuilder: QueryBuilder) {
        throw UnsupportedByDialectException("YDB does not support GROUP_CONCAT", currentDialect)
    }

    override fun <T : String?> locate(queryBuilder: QueryBuilder, expr: Expression<T>, substring: String) =
        queryBuilder {
            append("FIND(", expr, ", '", substring, "')")
        }

    override fun <T : String?> trim(queryBuilder: QueryBuilder, expr: Expression<T>) {
        throw UnsupportedByDialectException("YDB does not support TRIM", currentDialect)
    }

    override fun upsert(
        table: Table,
        data: List<Pair<Column<*>, Any?>>,
        expression: String,
        onUpdate: List<Pair<Column<*>, Any?>>,
        keyColumns: List<Column<*>>,
        where: Op<Boolean>?,
        transaction: Transaction
    ): String {
        val columns = data.map { it.first }
        val columnsExpr = columns.takeIf { it.isNotEmpty() }?.joinToString(prefix = "(", postfix = ")") { transaction.identity(it) } ?: ""

        return "UPSERT INTO ${transaction.identity(table)} $columnsExpr $expression"
    }
}
